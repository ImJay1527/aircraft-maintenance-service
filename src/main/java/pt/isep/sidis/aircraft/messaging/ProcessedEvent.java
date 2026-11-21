package pt.isep.sidis.aircraft.messaging;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Idempotent Consumer Pattern entity.
 * Persists processed event IDs to prevent duplicate state mutation during at-least-once message delivery.
 */
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_processed_event_id", columnList = "event_id", unique = true)
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "event_type", length = 128)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
