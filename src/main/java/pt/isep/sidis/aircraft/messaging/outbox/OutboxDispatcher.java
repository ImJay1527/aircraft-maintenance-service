package pt.isep.sidis.aircraft.messaging.outbox;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.messaging.RabbitMQConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches pending outbox events asynchronously to RabbitMQ broker.
 * Implements resilient retry with at-least-once delivery guarantee.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "outbox-dispatcher"));
    private final AtomicBoolean wakeUpQueued = new AtomicBoolean(false);

    public OutboxDispatcher(OutboxEventRepository outboxRepository,
                            RabbitTemplate rabbitTemplate,
                            @Value("${aisafe.rabbitmq.exchange:" + RabbitMQConfig.EXCHANGE_NAME + "}") String exchangeName,
                            @Value("${aisafe.outbox.interval-ms:1000}") long intervalMs) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        executor.scheduleWithFixedDelay(this::dispatchPendingSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void wakeUp() {
        if (wakeUpQueued.compareAndSet(false, true)) {
            executor.execute(() -> {
                wakeUpQueued.set(false);
                dispatchPendingSafely();
            });
        }
    }

    private void dispatchPendingSafely() {
        try {
            dispatchPending();
        } catch (Exception e) {
            log.warn("Outbox dispatch run failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void dispatchPending() {
        List<OutboxEvent> pending = outboxRepository.findTop50ByStatusOrderByIdAsc("PENDING");
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("eventId", event.getEventId())
                        .setHeader("eventType", event.getEventType())
                        .build();

                rabbitTemplate.send(exchangeName, event.getRoutingKey(), message);
                event.markPublished();
                log.info("Outbox published event [{}] ({}) to exchange [{}] with routing key [{}]",
                        event.getEventId(), event.getEventType(), exchangeName, event.getRoutingKey());
            } catch (AmqpException e) {
                event.incrementRetry();
                log.warn("Failed to publish outbox event [{}] to RabbitMQ: {}. Broker offline; will retry.",
                        event.getEventId(), e.getMessage());
                break; // stop current batch if broker unreachable
            }
        }
        outboxRepository.saveAll(pending);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
