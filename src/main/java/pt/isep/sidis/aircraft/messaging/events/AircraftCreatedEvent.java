package pt.isep.sidis.aircraft.messaging.events;

import java.io.Serializable;
import java.time.Instant;

public record AircraftCreatedEvent(
    String registration,
    String serialNumber,
    String modelCode,
    String modelName,
    Double maxRange,
    Double fuelCapacity,
    Integer activeCapacity,
    Instant timestamp
) implements Serializable {
    public AircraftCreatedEvent(String registration, String serialNumber, String modelCode,
                                String modelName, Double maxRange, Double fuelCapacity, Integer activeCapacity) {
        this(registration, serialNumber, modelCode, modelName, maxRange, fuelCapacity, activeCapacity, Instant.now());
    }
}
