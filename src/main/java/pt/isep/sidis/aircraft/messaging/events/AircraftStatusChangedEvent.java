package pt.isep.sidis.aircraft.messaging.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftStatusChangedEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    String registration,
    @JsonAlias({"previousStatus", "oldStatus"})
    String previousStatus,
    String newStatus,
    Double totalFlightHours
) implements Serializable {

    public AircraftStatusChangedEvent(String registration, String oldStatus, String newStatus, Double totalFlightHours) {
        this(
            UUID.randomUUID().toString(),
            "AircraftStatusChangedEvent",
            Instant.now(),
            registration,
            oldStatus,
            newStatus,
            totalFlightHours
        );
    }

    public String oldStatus() {
        return previousStatus;
    }

    public Instant timestamp() {
        return occurredAt != null ? occurredAt : Instant.now();
    }
}
