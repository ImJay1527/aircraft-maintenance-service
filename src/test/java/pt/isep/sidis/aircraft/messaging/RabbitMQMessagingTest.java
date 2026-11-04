package pt.isep.sidis.aircraft.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.isep.sidis.aircraft.messaging.events.AircraftCreatedEvent;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceCompletedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceScheduledEvent;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
public class RabbitMQMessagingTest {

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Test
    void shouldPublishAircraftStatusChangedWithoutCrashingWhenBrokerOffline() {
        AircraftStatusChangedEvent event = new AircraftStatusChangedEvent(
                "CS-TPA", "AVAILABLE", "UNDER_MAINTENANCE", 1200.0
        );
        assertThatCode(() -> eventPublisher.publishAircraftStatusChanged(event))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPublishAircraftCreatedWithoutCrashingWhenBrokerOffline() {
        AircraftCreatedEvent event = new AircraftCreatedEvent(
                "CS-TEST", "SN-1234", "A320neo", "Airbus", 6300.0, 24000.0, 180
        );
        assertThatCode(() -> eventPublisher.publishAircraftCreated(event))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPublishMaintenanceScheduledWithoutCrashingWhenBrokerOffline() {
        MaintenanceScheduledEvent event = new MaintenanceScheduledEvent(
                10L, "CS-TPA", 1L, "Routine A-Check", LocalDate.now(), 6.0, "ENGINE", 1500.0
        );
        assertThatCode(() -> eventPublisher.publishMaintenanceScheduled(event))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPublishMaintenanceCompletedWithoutCrashingWhenBrokerOffline() {
        MaintenanceCompletedEvent event = new MaintenanceCompletedEvent(
                10L, "CS-TPA", 6.0, 1500.0, "All items checked"
        );
        assertThatCode(() -> eventPublisher.publishMaintenanceCompleted(event))
                .doesNotThrowAnyException();
    }
}
