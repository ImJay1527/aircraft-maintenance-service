package pt.isep.sidis.aircraft.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.events.FlightCompletedEvent;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.util.Optional;

/**
 * AMQP Message Consumer for events arriving from flight-operations-service.
 * Demonstrates asynchronous inter-service integration via RabbitMQ.
 */
@Component
public class FlightEventsListener {

    private static final Logger logger = LoggerFactory.getLogger(FlightEventsListener.class);

    private final AircraftRepository aircraftRepository;
    private final DomainEventPublisher eventPublisher;

    public FlightEventsListener(AircraftRepository aircraftRepository, DomainEventPublisher eventPublisher) {
        this.aircraftRepository = aircraftRepository;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = "${aisafe.rabbitmq.aircraft-queue:" + RabbitMQConfig.AIRCRAFT_QUEUE_NAME + "}",
                    autoStartup = "${aisafe.rabbitmq.listener.auto-startup:false}")
    @Transactional
    public void onFlightCompleted(FlightCompletedEvent event) {
        logger.info("Received AMQP FlightCompletedEvent: flight {} on aircraft {} (duration: {} hrs)",
                event.flightNumber(), event.aircraftRegistration(), event.durationHours());

        if (event.aircraftRegistration() == null || event.durationHours() == null || event.durationHours() <= 0) {
            logger.warn("Discarding invalid FlightCompletedEvent: {}", event);
            return;
        }

        Optional<Aircraft> opt = aircraftRepository.findByRegistrationNumber(event.aircraftRegistration().toUpperCase());
        if (opt.isPresent()) {
            Aircraft aircraft = opt.get();
            aircraft.addFlightHours(event.durationHours());
            // If the aircraft was IN_FLIGHT, mark it back to AVAILABLE
            if (aircraft.getStatus() == AircraftStatus.IN_FLIGHT) {
                aircraft.updateStatus(AircraftStatus.AVAILABLE);
            }
            aircraftRepository.save(aircraft);

            // Re-publish status changed event to update CQRS read projections
            eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                    aircraft.getRegistrationNumber(),
                    AircraftStatus.IN_FLIGHT.name(),
                    aircraft.getStatus().name(),
                    aircraft.getTotalFlightHours()
            ));

            logger.info("Updated aircraft {} total flight hours to {} after flight {}",
                    aircraft.getRegistrationNumber(), aircraft.getTotalFlightHours(), event.flightNumber());
        } else {
            logger.debug("Aircraft {} not managed by this local instance shard; event ignored.", event.aircraftRegistration());
        }
    }
}
