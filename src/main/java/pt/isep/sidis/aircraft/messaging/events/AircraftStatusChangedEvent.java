package pt.isep.sidis.aircraft.messaging.events;

import java.io.Serializable;
import java.time.Instant;

public record AircraftStatusChangedEvent(
    String registration,
    String oldStatus,
    String newStatus,
    Double totalFlightHours,
    Instant timestamp
) implements Serializable {
    public AircraftStatusChangedEvent(String registration, String oldStatus, String newStatus, Double totalFlightHours) {
        this(registration, oldStatus, newStatus, totalFlightHours, Instant.now());
    }
}
