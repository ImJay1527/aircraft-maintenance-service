package pt.isep.sidis.aircraft.cqrs.projections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.events.AircraftCreatedEvent;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceCompletedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceScheduledEvent;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CQRS Read-Side Projection for Aircraft Mood.
 * Decoupled from write-side entities.
 * Persisted in dedicated table (aircraft_mood_views) so moods survive restarts.
 * Automatically projected/updated whenever domain events occur.
 */
@Component
public class AircraftMoodProjection {

    private static final Logger logger = LoggerFactory.getLogger(AircraftMoodProjection.class);

    private final AircraftMoodRepository moodRepository;
    private final AircraftRepository aircraftRepository;

    public AircraftMoodProjection(AircraftMoodRepository moodRepository,
                                  AircraftRepository aircraftRepository) {
        this.moodRepository = moodRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        Iterable<Aircraft> all = aircraftRepository.findAll();
        for (Aircraft a : all) {
            String reg = a.getRegistrationNumber().toUpperCase();
            if (!moodRepository.existsById(reg)) {
                updateMoodView(
                        reg,
                        a.getModel() != null ? a.getModel().getName() : "Unknown",
                        a.getStatus().name(),
                        a.getTotalFlightHours()
                );
            }
        }
        logger.info("AircraftMoodProjection initialized/retained {} persistent aircraft mood views from DB", moodRepository.count());
    }

    @EventListener
    @Transactional
    public void onAircraftCreated(AircraftCreatedEvent event) {
        updateMoodView(event.registration(), event.modelName(), "AVAILABLE", 0.0);
        logger.info("Projected mood for newly created aircraft {}", event.registration());
    }

    @EventListener
    @Transactional
    public void onAircraftStatusChanged(AircraftStatusChangedEvent event) {
        Optional<AircraftMoodEntity> existingOpt = moodRepository.findById(event.registration().toUpperCase());
        if (existingOpt.isPresent()) {
            AircraftMoodEntity existing = existingOpt.get();
            if ("REJUVENATED".equals(existing.getMood()) && "AVAILABLE".equals(event.newStatus())) {
                // Preserve REJUVENATED mood when transitioning to AVAILABLE from completed maintenance
                return;
            }
        }

        String model = existingOpt.map(AircraftMoodEntity::getModel).orElse("Unknown");
        Double hours = event.totalFlightHours() != null ? event.totalFlightHours()
                : existingOpt.map(AircraftMoodEntity::getTotalFlightHours).orElse(0.0);

        updateMoodView(event.registration().toUpperCase(), model, event.newStatus(), hours);
        logger.info("Projected mood change for aircraft {}: new status {}", event.registration(), event.newStatus());
    }

    @EventListener
    @Transactional
    public void onMaintenanceScheduled(MaintenanceScheduledEvent event) {
        String reg = event.registration().toUpperCase();
        Optional<AircraftMoodEntity> existingOpt = moodRepository.findById(reg);
        if (existingOpt.isPresent()) {
            AircraftMoodEntity existing = existingOpt.get();
            existing.setMood("ANTICIPATING");
            existing.setEnergyLevel(75);
            existing.setSparkleFactor(4.0);
            existing.setStatusMessage("Looking forward to a spa day at maintenance on " + event.scheduledDate());
            existing.setLastUpdated(Instant.now());
            moodRepository.save(existing);
            logger.info("Projected ANTICIPATING mood for aircraft {} scheduled on {}", reg, event.scheduledDate());
        }
    }

    @EventListener
    @Transactional
    public void onMaintenanceCompleted(MaintenanceCompletedEvent event) {
        String reg = event.registration().toUpperCase();
        Optional<AircraftMoodEntity> existingOpt = moodRepository.findById(reg);
        if (existingOpt.isPresent()) {
            AircraftMoodEntity existing = existingOpt.get();
            existing.setMood("REJUVENATED");
            existing.setEnergyLevel(100);
            existing.setSparkleFactor(5.0);
            existing.setStatusMessage("Fresh out of maintenance! Engines humming happily. " + event.completionDetails());
            existing.setOperationalStatus("AVAILABLE");
            existing.setLastUpdated(Instant.now());
            moodRepository.save(existing);
            logger.info("Projected REJUVENATED mood for aircraft {}", reg);
        }
    }

    @Transactional(readOnly = true)
    public List<AircraftMoodView> findAll() {
        return moodRepository.findAll().stream().map(AircraftMoodEntity::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AircraftMoodView> findByRegistration(String registration) {
        if (registration == null) {
            return Optional.empty();
        }
        return moodRepository.findById(registration.toUpperCase()).map(AircraftMoodEntity::toView);
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

        AircraftMoodEntity entity = new AircraftMoodEntity(
                registration,
                model != null ? model : "Unknown",
                mood,
                energy,
                sparkle,
                message,
                status.name(),
                flightHours != null ? flightHours : 0.0,
                Instant.now()
        );

        moodRepository.save(entity);
    }
}
