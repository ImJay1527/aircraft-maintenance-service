package pt.isep.sidis.aircraft.messaging.events;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record MaintenanceScheduledEvent(
    Long recordId,
    String registration,
    Long templateId,
    String description,
    LocalDate scheduledDate,
    Double estimatedHours,
    String component,
    Double estimatedCost,
    Instant timestamp
) implements Serializable {
    public MaintenanceScheduledEvent(Long recordId, String registration, Long templateId,
                                     String description, LocalDate scheduledDate, Double estimatedHours,
                                     String component, Double estimatedCost) {
        this(recordId, registration, templateId, description, scheduledDate, estimatedHours, component, estimatedCost, Instant.now());
    }
}
