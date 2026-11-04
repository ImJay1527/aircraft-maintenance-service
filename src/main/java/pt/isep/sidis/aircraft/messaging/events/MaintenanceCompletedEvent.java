package pt.isep.sidis.aircraft.messaging.events;

import java.io.Serializable;
import java.time.Instant;

public record MaintenanceCompletedEvent(
    Long recordId,
    String registration,
    Double actualHours,
    Double actualCost,
    String completionDetails,
    Instant timestamp
) implements Serializable {
    public MaintenanceCompletedEvent(Long recordId, String registration, Double actualHours,
                                     Double actualCost, String completionDetails) {
        this(recordId, registration, actualHours, actualCost, completionDetails, Instant.now());
    }
}
