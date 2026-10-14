package pt.isep.sidis.aircraft.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.time.LocalDate;

/**
 * Bootstraps initial aircraft models and aircraft instances.
 * Follows the service contract specified in HANDOFF.md and docs/service-contracts.md,
 * allowing flight-operations-service and Postman test collections to validate real bookings.
 */
@Component
public class Bootstrapper implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);

    private final AircraftModelRepository modelRepository;
    private final AircraftRepository aircraftRepository;

    public Bootstrapper(AircraftModelRepository modelRepository, AircraftRepository aircraftRepository) {
        this.modelRepository = modelRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        bootstrapAircraftModels();
        bootstrapAircrafts();
    }

    private void bootstrapAircraftModels() {
        if (modelRepository.count() == 0) {
            logger.info("Bootstrapping Aircraft Models...");
            modelRepository.save(new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828));
            modelRepository.save(new AircraftModel("737 MAX", "Boeing", 180, 26000, 6500, 840));
            modelRepository.save(new AircraftModel("777X", "Boeing", 400, 35000, 8000, 900));
            modelRepository.save(new AircraftModel("A350", "Airbus", 350, 140000, 15000, 903));
            logger.info("Aircraft Models bootstrapped successfully.");
        }
    }

    private void bootstrapAircrafts() {
        if (aircraftRepository.count() == 0) {
            logger.info("Bootstrapping Aircrafts from service contract...");

            AircraftModel a320 = modelRepository.findByName("A320neo").orElseThrow();
            AircraftModel b737 = modelRepository.findByName("737 MAX").orElseThrow();
            AircraftModel b777 = modelRepository.findByName("777X").orElseThrow();
            AircraftModel a350 = modelRepository.findByName("A350").orElseThrow();

            // A320neo Available
            aircraftRepository.save(new Aircraft("CS-TPA", a320, LocalDate.of(2024, 1, 15), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPC", a320, LocalDate.of(2020, 11, 5), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPF", a320, LocalDate.of(2019, 8, 20), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPH", a320, LocalDate.of(2023, 4, 18), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPJ", a320, LocalDate.of(2024, 3, 1), AircraftStatus.AVAILABLE));

            // 737 MAX Available
            aircraftRepository.save(new Aircraft("CS-TPB", b737, LocalDate.of(2023, 6, 20), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPG", b737, LocalDate.of(2022, 2, 14), AircraftStatus.AVAILABLE));
            aircraftRepository.save(new Aircraft("CS-TPI", b737, LocalDate.of(2021, 9, 9), AircraftStatus.AVAILABLE));

            // 777X Available
            aircraftRepository.save(new Aircraft("CS-TPD", b777, LocalDate.of(2022, 5, 10), AircraftStatus.AVAILABLE));

            // A350 In-flight
            aircraftRepository.save(new Aircraft("CS-TPE", a350, LocalDate.of(2021, 3, 10), AircraftStatus.IN_FLIGHT));

            // A320neo Under maintenance
            aircraftRepository.save(new Aircraft("CS-TPM", a320, LocalDate.of(2020, 5, 12), AircraftStatus.UNDER_MAINTENANCE));

            logger.info("Aircrafts bootstrapped successfully.");
        }
    }
}
