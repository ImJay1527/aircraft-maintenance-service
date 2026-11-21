package pt.isep.sidis.aircraft.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodProjection;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodView;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.events.FlightCompletedEvent;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class FlightEventsListenerTest {

    @Autowired
    private FlightEventsListener listener;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private AircraftMoodProjection moodProjection;

    @Test
    void shouldAccumulateFlightHoursAndRefreshMoodOnFlightCompletedEvent() {
        // Arrange
        String reg = "CS-TPA";
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(reg).orElseThrow();
        Double initialHours = aircraft.getTotalFlightHours();
        aircraft.updateStatus(AircraftStatus.IN_FLIGHT);
        aircraftRepository.save(aircraft);

        // Act: Simulate receiving AMQP event from flight-operations-service
        listener.onFlightCompleted(new FlightCompletedEvent("TP502", reg, 3.5));

        // Assert: Hours accumulated & status returned to AVAILABLE
        Aircraft updated = aircraftRepository.findByRegistrationNumber(reg).orElseThrow();
        assertThat(updated.getTotalFlightHours()).isEqualTo(initialHours + 3.5);
        assertThat(updated.getStatus()).isEqualTo(AircraftStatus.AVAILABLE);

        // Assert: CQRS mood projection updated reactively
        Optional<AircraftMoodView> mood = moodProjection.findByRegistration(reg);
        assertThat(mood).isPresent();
        assertThat(mood.get().operationalStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void shouldIgnoreGracefullyWhenAircraftNotOnThisShard() {
        // Act: Aircraft not on this shard
        listener.onFlightCompleted(new FlightCompletedEvent("TP999", "CS-UNKNOWN-SHARD", 4.0));
        // Assert: No exception thrown, handled cleanly
    }

    @Test
    void shouldCleanlyIgnoreNonCompletedFlightEventsViaAmqpMessage() {
        org.springframework.amqp.core.Message msg = org.springframework.amqp.core.MessageBuilder
                .withBody("{\"flightNumber\":\"TP101\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();

        // Should not throw and should cleanly ignore
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                listener.onAmqpMessage(msg, "flight.scheduled"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                listener.onAmqpMessage(msg, "flight.cancelled"));
    }

    @Test
    void shouldSynchronizeMoodProjectionWhenAircraftEventArrivesFromBroker() throws Exception {
        String json = """
                {
                    "eventId": "test-broker-event",
                    "eventType": "AircraftStatusChangedEvent",
                    "registration": "CS-TPA",
                    "previousStatus": "AVAILABLE",
                    "newStatus": "UNDER_MAINTENANCE",
                    "totalFlightHours": 1500.0
                }
                """;
        org.springframework.amqp.core.Message msg = org.springframework.amqp.core.MessageBuilder
                .withBody(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();

        listener.onAmqpMessage(msg, "aircraft.status.changed");

        Optional<AircraftMoodView> mood = moodProjection.findByRegistration("CS-TPA");
        assertThat(mood).isPresent();
        assertThat(mood.get().operationalStatus()).isEqualTo("UNDER_MAINTENANCE");
        assertThat(mood.get().mood()).isEqualTo("SLEEPY");
    }

    @Test
    void shouldNotAccumulateHoursTwiceWhenDuplicateFlightCompletedEventReceived() {
        String reg = "CS-TPB";
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(reg).orElseThrow();
        Double initialHours = aircraft.getTotalFlightHours();

        String duplicateEventId = "duplicate-flight-event-uuid-12345";
        FlightCompletedEvent event = new FlightCompletedEvent(
                duplicateEventId,
                "FlightCompletedEvent",
                java.time.Instant.now(),
                100L,
                "TP900",
                reg,
                "LIS",
                "OPO",
                2.0
        );

        // First delivery: should add 2.0 hours
        listener.onFlightCompleted(event);
        Aircraft updatedOnce = aircraftRepository.findByRegistrationNumber(reg).orElseThrow();
        assertThat(updatedOnce.getTotalFlightHours()).isEqualTo(initialHours + 2.0);

        // Second delivery (duplicate from broker redelivery): should be ignored by idempotent consumer
        listener.onFlightCompleted(event);
        Aircraft updatedTwice = aircraftRepository.findByRegistrationNumber(reg).orElseThrow();
        assertThat(updatedTwice.getTotalFlightHours()).isEqualTo(initialHours + 2.0);
    }
}
