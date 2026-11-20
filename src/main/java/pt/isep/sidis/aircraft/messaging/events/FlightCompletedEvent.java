package pt.isep.sidis.aircraft.messaging.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightCompletedEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    Long flightId,
    String flightNumber,
    @JsonAlias({"registration", "aircraftRegistration"})
    String registration,
    String originAirportIata,
    String destinationAirportIata,
    Double durationHours
) implements Serializable {

    public FlightCompletedEvent(String flightNumber, String aircraftRegistration, Double durationHours) {
        this(
            UUID.randomUUID().toString(),
            "FlightCompletedEvent",
            Instant.now(),
            null,
            flightNumber,
            aircraftRegistration,
            null,
            null,
            durationHours
        );
    }

    public String aircraftRegistration() {
        return registration;
    }

    public Instant timestamp() {
        return occurredAt != null ? occurredAt : Instant.now();
    }
}
