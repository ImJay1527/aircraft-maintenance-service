package pt.isep.sidis.aircraft.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.messaging.events.*;
import pt.isep.sidis.aircraft.messaging.outbox.OutboxService;

/**
 * Domain Event Publisher implementing the Transactional Outbox Pattern.
 * Saves domain events inside the transactional database boundary and
 * dispatches them to the RabbitMQ broker asynchronously, preventing phantom events.
 */
@Component
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher localEventPublisher;
    private final OutboxService outboxService;

    public DomainEventPublisher(ApplicationEventPublisher localEventPublisher,
                                OutboxService outboxService) {
        this.localEventPublisher = localEventPublisher;
        this.outboxService = outboxService;
    }

    @Transactional
    public void publishAircraftStatusChanged(AircraftStatusChangedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_STATUS, event.eventId(), "AircraftStatusChangedEvent", event);
    }

    @Transactional
    public void publishAircraftCreated(AircraftCreatedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_CREATED, event.eventId(), "AircraftCreatedEvent", event);
    }

    @Transactional
    public void publishMaintenanceScheduled(MaintenanceScheduledEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_MAINTENANCE_SCHEDULED, event.eventId(), "MaintenanceScheduledEvent", event);
    }

    @Transactional
    public void publishMaintenanceCompleted(MaintenanceCompletedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_MAINTENANCE_COMPLETED, event.eventId(), "MaintenanceCompletedEvent", event);
    }

    private void publish(String routingKey, String eventId, String eventType, Object event) {
        // 1. Publish locally for CQRS in-process projections
        try {
            localEventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish local in-process event: {}", e.getMessage());
        }

        // 2. Persist in Transactional Outbox for guaranteed, resilient RabbitMQ delivery
        try {
            outboxService.enqueue(routingKey, eventId, eventType, event);
            logger.debug("Enqueued event [{}] ({}) in transactional outbox for routing key [{}]",
                    eventId, eventType, routingKey);
        } catch (Exception e) {
            logger.error("Failed to enqueue event [{}] in outbox: {}", eventId, e.getMessage(), e);
            throw e;
        }
    }
}
