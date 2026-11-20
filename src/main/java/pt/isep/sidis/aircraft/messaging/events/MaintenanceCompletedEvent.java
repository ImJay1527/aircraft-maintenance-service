package pt.isep.sidis.aircraft.messaging.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceCompletedEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    Long recordId,
    String registration,
    @JsonAlias({"actualHours", "loggedHours"})
    Double actualHours,
    @JsonAlias({"actualCost", "totalCost"})
    Double actualCost,
    String completionDetails
) implements Serializable {

    public MaintenanceCompletedEvent(Long recordId, String registration, Double actualHours,
                                     Double actualCost, String completionDetails) {
        this(
            UUID.randomUUID().toString(),
            "MaintenanceCompletedEvent",
            Instant.now(),
            recordId,
            registration,
            actualHours,
            actualCost,
            completionDetails
        );
    }

    public Double loggedHours() {
        return actualHours;
    }

    public Double totalCost() {
        return actualCost;
    }

    public Instant timestamp() {
        return occurredAt != null ? occurredAt : Instant.now();
    }
}
