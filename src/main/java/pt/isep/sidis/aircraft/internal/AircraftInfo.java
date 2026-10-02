package pt.isep.sidis.aircraft.internal;

/**
 * Contract with flight-operations-service: response of GET /internal/aircraft/{registration}.
 * Do NOT rename or remove fields without agreeing with the Flight Operations owner (see README).
 */
public record AircraftInfo(
        String registrationNumber,
        String status,          // AVAILABLE | IN_FLIGHT | UNDER_MAINTENANCE | INACTIVE
        String modelName,
        double maxRange,        // km
        double fuelCapacity,    // litres
        int activeCapacity) {   // seats in the aircraft's current configuration
}
