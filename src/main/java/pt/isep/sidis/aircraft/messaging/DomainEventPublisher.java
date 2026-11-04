package pt.isep.sidis.aircraft.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pt.isep.sidis.aircraft.messaging.events.*;

@Component
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher localEventPublisher;
    private final String exchangeName;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate,
                                ApplicationEventPublisher localEventPublisher,
                                @Value("${aisafe.rabbitmq.exchange:" + RabbitMQConfig.EXCHANGE_NAME + "}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.localEventPublisher = localEventPublisher;
        this.exchangeName = exchangeName;
    }

    public void publishAircraftStatusChanged(AircraftStatusChangedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_STATUS, event);
    }

    public void publishAircraftCreated(AircraftCreatedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_CREATED, event);
    }

    public void publishMaintenanceScheduled(MaintenanceScheduledEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_MAINTENANCE_SCHEDULED, event);
    }

    public void publishMaintenanceCompleted(MaintenanceCompletedEvent event) {
        publish(RabbitMQConfig.ROUTING_KEY_MAINTENANCE_COMPLETED, event);
    }

    private void publish(String routingKey, Object event) {
        // 1. Publish locally for CQRS in-process projections
        try {
            localEventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish local in-process event: {}", e.getMessage());
        }

        // 2. Publish to RabbitMQ broker for inter-service integration
        try {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
            logger.info("Published event to RabbitMQ [{}] with routing key [{}]: {}", exchangeName, routingKey, event);
        } catch (AmqpException e) {
            logger.warn("RabbitMQ unavailable. Event [{}] with routing key [{}] could not be delivered to broker: {}",
                    event.getClass().getSimpleName(), routingKey, e.getMessage());
        }
    }
}
