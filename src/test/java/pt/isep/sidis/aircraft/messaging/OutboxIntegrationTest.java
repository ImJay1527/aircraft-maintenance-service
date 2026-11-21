package pt.isep.sidis.aircraft.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.outbox.OutboxDispatcher;
import pt.isep.sidis.aircraft.messaging.outbox.OutboxEvent;
import pt.isep.sidis.aircraft.messaging.outbox.OutboxEventRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OutboxIntegrationTest {

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Test
    @Transactional
    void shouldEnqueueEventInOutboxTableOnPublish() {
        long countBefore = outboxEventRepository.count();

        AircraftStatusChangedEvent event = new AircraftStatusChangedEvent(
                "CS-TPA", "AVAILABLE", "UNDER_MAINTENANCE", 1250.0
        );

        domainEventPublisher.publishAircraftStatusChanged(event);

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertThat(all.size()).isGreaterThan((int) countBefore);

        OutboxEvent last = all.get(all.size() - 1);
        assertThat(last.getRoutingKey()).isEqualTo(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_STATUS);
        assertThat(last.getPayload()).contains("CS-TPA");
        assertThat(last.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldHandleDispatcherRunSafelyWhenBrokerOffline() {
        outboxDispatcher.dispatchPending();
        // Dispatches without throwing uncaught exceptions even when RabbitMQ broker is offline
    }
}
