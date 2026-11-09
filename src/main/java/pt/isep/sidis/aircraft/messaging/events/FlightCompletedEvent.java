package pt.isep.sidis.aircraft.messaging.events;

import java.io.Serializable;
import java.time.Instant;

public record FlightCompletedEvent(
    String flightNumber,
    String aircraftRegistration,
    Double durationHours,
    Instant timestamp
) implements Serializable {
    public FlightCompletedEvent(String flightNumber, String aircraftRegistration, Double durationHours) {
        this(flightNumber, aircraftRegistration, durationHours, Instant.now());
    }
}
