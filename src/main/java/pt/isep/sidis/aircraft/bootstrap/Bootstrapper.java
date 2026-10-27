package pt.isep.sidis.aircraft.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceComponent;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceRecordRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceTemplateRepository;
import pt.isep.sidis.aircraft.services.MaintenanceRecordService;

import java.time.LocalDate;
import java.util.List;

/**
 * Bootstraps initial aircraft models, aircraft instances, maintenance templates, and maintenance records.
 * Follows the service contract specified in HANDOFF.md and docs/service-contracts.md,
 * allowing flight-operations-service, maintenance flows, and Postman test collections to validate real operations.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "aircraft.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class Bootstrapper implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);

    private final AircraftModelRepository modelRepository;
    private final AircraftRepository aircraftRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceRecordService recordService;
    private final pt.isep.sidis.aircraft.cluster.ClusterConfig clusterConfig;

    public Bootstrapper(AircraftModelRepository modelRepository,
                        AircraftRepository aircraftRepository,
                        MaintenanceTemplateRepository templateRepository,
                        MaintenanceRecordRepository recordRepository,
                        MaintenanceRecordService recordService,
                        pt.isep.sidis.aircraft.cluster.ClusterConfig clusterConfig) {
        this.modelRepository = modelRepository;
        this.aircraftRepository = aircraftRepository;
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.recordService = recordService;
        this.clusterConfig = clusterConfig;
    }

    @Override
    @Transactional
    public void run(String... args) {
        bootstrapAircraftModels();
        bootstrapAircrafts();
        bootstrapMaintenanceTemplates();
        bootstrapMaintenanceRecords();
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

            var optA320 = modelRepository.findByName("A320neo");
            if (optA320.isEmpty()) return;
            AircraftModel a320 = optA320.get();
            AircraftModel b737 = modelRepository.findByName("737 MAX").orElse(null);
            AircraftModel b777 = modelRepository.findByName("777X").orElse(null);
            AircraftModel a350 = modelRepository.findByName("A350").orElse(null);
            if (b737 == null || b777 == null || a350 == null) return;

            // A320neo Available
            Aircraft a1 = new Aircraft("CS-TPA", a320, LocalDate.of(2024, 1, 15), AircraftStatus.AVAILABLE);
            a1.addFlightHours(1500.0);
            aircraftRepository.save(a1);

            Aircraft a2 = new Aircraft("CS-TPC", a320, LocalDate.of(2020, 11, 5), AircraftStatus.AVAILABLE);
            a2.addFlightHours(5000.0);
            aircraftRepository.save(a2);

            Aircraft a3 = new Aircraft("CS-TPF", a320, LocalDate.of(2019, 8, 20), AircraftStatus.AVAILABLE);
            a3.addFlightHours(9000.0);
            aircraftRepository.save(a3);

            Aircraft a4 = new Aircraft("CS-TPH", a320, LocalDate.of(2023, 4, 18), AircraftStatus.AVAILABLE);
            aircraftRepository.save(a4);

            Aircraft a5 = new Aircraft("CS-TPJ", a320, LocalDate.of(2024, 3, 1), AircraftStatus.AVAILABLE);
            aircraftRepository.save(a5);

            // 737 MAX Available
            Aircraft b1 = new Aircraft("CS-TPB", b737, LocalDate.of(2023, 6, 20), AircraftStatus.AVAILABLE);
            b1.addFlightHours(3000.0);
            aircraftRepository.save(b1);

            Aircraft b2 = new Aircraft("CS-TPG", b737, LocalDate.of(2022, 2, 14), AircraftStatus.AVAILABLE);
            b2.addFlightHours(12000.0);
            aircraftRepository.save(b2);

            Aircraft b3 = new Aircraft("CS-TPI", b737, LocalDate.of(2021, 9, 9), AircraftStatus.AVAILABLE);
            aircraftRepository.save(b3);

            // 777X Available
            Aircraft c1 = new Aircraft("CS-TPD", b777, LocalDate.of(2022, 5, 10), AircraftStatus.AVAILABLE);
            c1.addFlightHours(8000.0);
            aircraftRepository.save(c1);

            // A350 In-flight
            Aircraft d1 = new Aircraft("CS-TPE", a350, LocalDate.of(2021, 3, 10), AircraftStatus.IN_FLIGHT);
            d1.addFlightHours(6000.0);
            aircraftRepository.save(d1);

            // A320neo Under maintenance
            Aircraft m1 = new Aircraft("CS-TPM", a320, LocalDate.of(2020, 5, 12), AircraftStatus.AVAILABLE);
            aircraftRepository.save(m1);

            // Sharded / partition-specific data to demonstrate P2P forwarding
            String self = clusterConfig.getSelfName();
            if (self != null && (self.contains("1") || self.equalsIgnoreCase("default"))) {
                Aircraft y1 = new Aircraft("CS-TPY", b737, LocalDate.of(2023, 7, 10), AircraftStatus.AVAILABLE);
                y1.addFlightHours(1800.0);
                aircraftRepository.save(y1);
            }

            if (self != null && self.contains("2")) {
                Aircraft x1 = new Aircraft("CS-TPX", a320, LocalDate.of(2022, 10, 1), AircraftStatus.AVAILABLE);
                x1.addFlightHours(2500.0);
                aircraftRepository.save(x1);
            }

            logger.info("Aircrafts bootstrapped successfully for [{}]", clusterConfig.getSelfName());
        }
    }

    private void bootstrapMaintenanceTemplates() {
        if (templateRepository.count() == 0) {
            logger.info("Bootstrapping Maintenance Templates...");
            AircraftModel a320 = modelRepository.findByName("A320neo").orElseThrow();
            AircraftModel b737 = modelRepository.findByName("737 MAX").orElseThrow();
            AircraftModel b777 = modelRepository.findByName("777X").orElseThrow();
            AircraftModel a350 = modelRepository.findByName("A350").orElseThrow();

            templateRepository.save(new MaintenanceTemplate(
                    "A-Check Routine Inspection",
                    TemplateType.INSPECTION,
                    8.0,
                    List.of(a320, b737),
                    List.of(
                            "Visual inspection of fuselage and wings",
                            "Check tyre pressure and brake wear",
                            "Inspect cabin emergency equipment",
                            "Verify navigation lights operation"
                    )
            ));

            templateRepository.save(new MaintenanceTemplate(
                    "C-Check Scheduled Maintenance",
                    TemplateType.SCHEDULED_MAINTENANCE,
                    72.0,
                    List.of(a320, b737, b777, a350),
                    List.of(
                            "Detailed structural inspection",
                            "Lubrication of moving components",
                            "Hydraulic system pressure test",
                            "Avionics software update check",
                            "Cabin interior deep cleaning"
                    )
            ));

            templateRepository.save(new MaintenanceTemplate(
                    "Engine Overhaul",
                    TemplateType.OVERHAUL,
                    160.0,
                    List.of(b777, a350, b737),
                    List.of(
                            "Full engine disassembly",
                            "Turbine blade inspection and replacement",
                            "Combustion chamber inspection",
                            "Reassembly and test-bench run",
                            "Final certification test flight"
                    )
            ));

            templateRepository.save(new MaintenanceTemplate(
                    "Cabin WiFi Retrofit",
                    TemplateType.MODIFICATION,
                    24.0,
                    List.of(a320, b737),
                    List.of(
                            "Install satellite antenna housing",
                            "Route cabin network cabling",
                            "Install and configure WiFi access points",
                            "Functional connectivity test"
                    )
            ));
            logger.info("Maintenance Templates bootstrapped successfully.");
        }
    }

    private void bootstrapMaintenanceRecords() {
        if (recordRepository.count() == 0) {
            logger.info("Bootstrapping Maintenance Records...");
            var optACheck = templateRepository.findByTemplateName("A-Check Routine Inspection");
            var optCCheck = templateRepository.findByTemplateName("C-Check Scheduled Maintenance");
            if (optACheck.isEmpty() || optCCheck.isEmpty()) return;
            Long aCheckId = optACheck.get().getId();
            Long cCheckId = optCCheck.get().getId();

            // Completed maintenance record on CS-TPC
            var rec1 = recordService.createRecord("CS-TPC", aCheckId, "Routine A-Check before flight rotation",
                    LocalDate.now().minusDays(30), null, MaintenanceComponent.AIRFRAME, 1200.0);
            if (rec1 == null) return;
            rec1 = recordService.startRecord(rec1.getId(), rec1.getVersion());
            if (rec1 == null) return;
            recordService.completeRecord(rec1.getId(), rec1.getVersion(), "All inspection items passed.", 7.5, 1150.0);

            // In-progress maintenance record on CS-TPM -> transitions CS-TPM to UNDER_MAINTENANCE
            var rec2 = recordService.createRecord("CS-TPM", cCheckId, "Scheduled C-Check in progress",
                    LocalDate.now().minusDays(2), null, MaintenanceComponent.AIRFRAME, 8500.0);
            if (rec2 == null) return;
            recordService.startRecord(rec2.getId(), rec2.getVersion());

            logger.info("Maintenance Records bootstrapped successfully.");
        }
    }
}
