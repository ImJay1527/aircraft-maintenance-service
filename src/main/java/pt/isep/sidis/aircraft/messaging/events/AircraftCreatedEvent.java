package pt.isep.sidis.aircraft.messaging.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftCreatedEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    String registration,
    String serialNumber,
    String modelCode,
    String modelName,
    Double maxRange,
    Double fuelCapacity,
    Double fuelBurnRate,
    @JsonAlias({"seatingCapacity", "activeCapacity"})
    Integer seatingCapacity,
    String status,
    Double flightHours
) implements Serializable {

    public AircraftCreatedEvent(String registration, String serialNumber, String modelCode,
                                String modelName, Double maxRange, Double fuelCapacity, Integer seatingCapacity) {
        this(
            UUID.randomUUID().toString(),
            "AircraftCreatedEvent",
            Instant.now(),
            registration,
            serialNumber,
            modelCode,
            modelName,
            maxRange,
            fuelCapacity,
            (fuelCapacity != null && maxRange != null && maxRange > 0) ? (fuelCapacity / maxRange) : null,
            seatingCapacity,
            "AVAILABLE",
            0.0
        );
    }

    public Integer activeCapacity() {
        return seatingCapacity;
    }

    public Instant timestamp() {
        return occurredAt != null ? occurredAt : Instant.now();
    }
}
