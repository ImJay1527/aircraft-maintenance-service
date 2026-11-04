package pt.isep.sidis.aircraft.cqrs.projections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.events.AircraftCreatedEvent;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceCompletedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceScheduledEvent;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CQRS Read-Side Projection for Aircraft Mood.
 * Decoupled from write-side entities.
 * Automatically projected/updated whenever domain events occur.
 */
@Component
public class AircraftMoodProjection {

    private static final Logger logger = LoggerFactory.getLogger(AircraftMoodProjection.class);

    private final Map<String, AircraftMoodView> moodViews = new ConcurrentHashMap<>();
    private final AircraftRepository aircraftRepository;

    public AircraftMoodProjection(AircraftRepository aircraftRepository) {
        this.aircraftRepository = aircraftRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // Initial projection from current database state on startup
        Iterable<Aircraft> all = aircraftRepository.findAll();
        for (Aircraft a : all) {
            updateMoodView(a.getRegistrationNumber(),
                    a.getModel().getName(),
                    a.getStatus().name(),
                    a.getTotalFlightHours());
        }
        logger.info("AircraftMoodProjection initialized with {} aircraft views", moodViews.size());
    }

    @EventListener
    public void onAircraftCreated(AircraftCreatedEvent event) {
        updateMoodView(event.registration(), event.modelName(), "AVAILABLE", 0.0);
        logger.info("Projected mood for newly created aircraft {}", event.registration());
    }

    @EventListener
    public void onAircraftStatusChanged(AircraftStatusChangedEvent event) {
        AircraftMoodView existing = moodViews.get(event.registration());
        if (existing != null && "REJUVENATED".equals(existing.mood()) && "AVAILABLE".equals(event.newStatus())) {
            // Preserve REJUVENATED mood when transitioning to AVAILABLE from completed maintenance
            return;
        }

        String model = existing != null ? existing.model() : "Unknown";
        Double hours = event.totalFlightHours() != null ? event.totalFlightHours()
                : (existing != null ? existing.totalFlightHours() : 0.0);

        updateMoodView(event.registration(), model, event.newStatus(), hours);
        logger.info("Projected mood change for aircraft {}: new status {}", event.registration(), event.newStatus());
    }

    @EventListener
    public void onMaintenanceScheduled(MaintenanceScheduledEvent event) {
        AircraftMoodView existing = moodViews.get(event.registration());
        if (existing != null) {
            moodViews.put(event.registration(), new AircraftMoodView(
                    existing.registration(),
                    existing.model(),
                    "ANTICIPATING",
                    75,
                    4.0,
                    "Looking forward to a spa day at maintenance on " + event.scheduledDate(),
                    existing.operationalStatus(),
                    existing.totalFlightHours(),
                    Instant.now()
            ));
        }
    }

    @EventListener
    public void onMaintenanceCompleted(MaintenanceCompletedEvent event) {
        AircraftMoodView existing = moodViews.get(event.registration());
        if (existing != null) {
            moodViews.put(event.registration(), new AircraftMoodView(
                    existing.registration(),
                    existing.model(),
                    "REJUVENATED",
                    100,
                    5.0,
                    "Fresh out of maintenance! Engines humming happily. " + event.completionDetails(),
                    "AVAILABLE",
                    existing.totalFlightHours(),
                    Instant.now()
            ));
        }
    }

    public List<AircraftMoodView> findAll() {
        return new ArrayList<>(moodViews.values());
    }

    public Optional<AircraftMoodView> findByRegistration(String registration) {
        return Optional.ofNullable(moodViews.get(registration));
    }

    private void updateMoodView(String registration, String model, String statusStr, Double flightHours) {
        String mood;
        int energy;
        double sparkle;
        String message;

        AircraftStatus status;
        try {
            status = AircraftStatus.valueOf(statusStr);
        } catch (Exception e) {
            status = AircraftStatus.AVAILABLE;
        }

        switch (status) {
            case IN_FLIGHT -> {
                mood = "EXCITED";
                energy = 98;
                sparkle = 5.0;
                message = "Cruising smoothly through the clouds at 35,000 feet!";
            }
            case UNDER_MAINTENANCE -> {
                mood = "SLEEPY";
                energy = 40;
                sparkle = 3.2;
                message = "Resting comfortably in the hangar getting TLC from certified mechanics.";
            }
            case INACTIVE -> {
                mood = "BORED";
                energy = 15;
                sparkle = 1.8;
                message = "Parked on the tarmac watching birds fly by. Ready for a flight plan!";
            }
            case AVAILABLE -> {
                if (flightHours != null && flightHours > 4000.0) {
                    mood = "EXPERIENCED";
                    energy = 85;
                    sparkle = 4.6;
                    message = "A seasoned queen of the skies, thoroughly inspected and reliable.";
                } else {
                    mood = "HAPPY";
                    energy = 92;
                    sparkle = 4.9;
                    message = "Sparkling clean, perfectly tuned, and eager to take off!";
                }
            }
            default -> {
                mood = "CONTENT";
                energy = 70;
                sparkle = 3.5;
                message = "Operational and stable.";
            }
        }

        AircraftMoodView view = new AircraftMoodView(
                registration,
                model,
                mood,
                energy,
                sparkle,
                message,
                status.name(),
                flightHours,
                Instant.now()
        );

        moodViews.put(registration, view);
    }
}
