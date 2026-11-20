package pt.isep.sidis.aircraft.messaging.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceScheduledEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    Long recordId,
    String registration,
    Long templateId,
    String description,
    LocalDate scheduledDate,
    Double estimatedHours,
    String component,
    Double estimatedCost
) implements Serializable {

    public MaintenanceScheduledEvent(Long recordId, String registration, Long templateId,
                                     String description, LocalDate scheduledDate, Double estimatedHours,
                                     String component, Double estimatedCost) {
        this(
            UUID.randomUUID().toString(),
            "MaintenanceScheduledEvent",
            Instant.now(),
            recordId,
            registration,
            templateId,
            description,
            scheduledDate,
            estimatedHours,
            component,
            estimatedCost
        );
    }

    public Instant timestamp() {
        return occurredAt != null ? occurredAt : Instant.now();
    }
}
