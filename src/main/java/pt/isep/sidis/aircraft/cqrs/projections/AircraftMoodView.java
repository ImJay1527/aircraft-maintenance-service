package pt.isep.sidis.aircraft.cqrs.projections;

import java.io.Serializable;
import java.time.Instant;

public record AircraftMoodView(
    String registration,
    String model,
    String mood,
    Integer energyLevel,
    Double sparkleFactor,
    String statusMessage,
    String operationalStatus,
    Double totalFlightHours,
    Instant lastUpdated
) implements Serializable {}
