package pt.isep.sidis.aircraft.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.events.*;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * AMQP Message Consumer for events arriving via RabbitMQ topic exchange.
 * Handles incoming flight events from flight-operations-service.
 */
@Component
public class FlightEventsListener {

    private static final Logger logger = LoggerFactory.getLogger(FlightEventsListener.class);

    private final AircraftRepository aircraftRepository;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    public FlightEventsListener(AircraftRepository aircraftRepository,
                                DomainEventPublisher eventPublisher,
                                ObjectMapper objectMapper,
                                ProcessedEventRepository processedEventRepository) {
        this.aircraftRepository = aircraftRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "${aisafe.rabbitmq.aircraft-queue:aircraft.events.${aircraft.instance-name:instance1}}",
                    autoStartup = "${aisafe.rabbitmq.listener.auto-startup:false}")
    @Transactional
    public void onAmqpMessage(Message message,
                              @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) throws Exception {
        if (routingKey == null) {
            routingKey = "";
        }

        try {
            if ("flight.completed".equalsIgnoreCase(routingKey)) {
                FlightCompletedEvent event = objectMapper.readValue(message.getBody(), FlightCompletedEvent.class);
                if ((event.eventId() == null || event.eventId().isBlank()) && message.getMessageProperties().getHeaders().containsKey("eventId")) {
                    String headerEventId = String.valueOf(message.getMessageProperties().getHeaders().get("eventId"));
                    event = new FlightCompletedEvent(
                            headerEventId,
                            event.eventType(),
                            event.occurredAt(),
                            event.flightId(),
                            event.flightNumber(),
                            event.aircraftRegistration(),
                            event.originAirportIata(),
                            event.destinationAirportIata(),
                            event.durationHours()
                    );
                }
                onFlightCompleted(event);
            } else {
                logger.debug("Cleanly ignored non-target AMQP event with routing key: {}", routingKey);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Rejecting poisoned AMQP message (unparseable payload) for routing key [{}]: {}", routingKey, e.getMessage());
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Poison message unparseable for routing key: " + routingKey, e);
        } catch (Exception e) {
            logger.warn("Failed to process AMQP message with routing key [{}]: {}", routingKey, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void onFlightCompleted(FlightCompletedEvent event) {
        String reg = event.aircraftRegistration();
        logger.info("Received AMQP FlightCompletedEvent: flight {} on aircraft {} (duration: {} hrs)",
                event.flightNumber(), reg, event.durationHours());

        if (reg == null || reg.isBlank() || event.durationHours() == null || event.durationHours() <= 0) {
            logger.debug("Discarding invalid FlightCompletedEvent: {}", event);
            return;
        }

        // Idempotent consumer: prevent double counting flight hours on duplicated redeliveries
        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank()) {
            if (processedEventRepository.existsByEventId(eventId)) {
                logger.info("Discarding duplicate FlightCompletedEvent with eventId [{}] (idempotent consumer)", eventId);
                return;
            }
            processedEventRepository.save(new ProcessedEvent(
                    eventId,
                    event.eventType() != null ? event.eventType() : "FlightCompletedEvent",
                    java.time.Instant.now()
            ));
        }

        Optional<Aircraft> opt = aircraftRepository.findByRegistrationNumber(reg.toUpperCase());
        if (opt.isPresent()) {
            Aircraft aircraft = opt.get();
            aircraft.addFlightHours(event.durationHours());
            AircraftStatus oldStatus = aircraft.getStatus();
            boolean statusChanged = false;

            // If the aircraft was IN_FLIGHT, mark it back to AVAILABLE
            if (oldStatus == AircraftStatus.IN_FLIGHT) {
                aircraft.updateStatus(AircraftStatus.AVAILABLE);
                statusChanged = true;
            }
            aircraftRepository.save(aircraft);

            // Re-publish status changed event only if the status actually changed
            if (statusChanged) {
                eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                        aircraft.getRegistrationNumber(),
                        oldStatus.name(),
                        aircraft.getStatus().name(),
                        aircraft.getTotalFlightHours()
                ));
            }

            logger.info("Updated aircraft {} total flight hours to {} after flight {}",
                    aircraft.getRegistrationNumber(), aircraft.getTotalFlightHours(), event.flightNumber());
        } else {
            logger.debug("Aircraft {} not managed by this local instance shard; event ignored.", reg);
        }
    }
}
