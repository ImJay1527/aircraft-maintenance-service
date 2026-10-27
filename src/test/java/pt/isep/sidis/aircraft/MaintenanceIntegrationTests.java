package pt.isep.sidis.aircraft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.api.dto.CancelMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.CompleteMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.CreateMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.CreateMaintenanceTemplateDTO;
import pt.isep.sidis.aircraft.api.dto.VersionedActionDTO;
import pt.isep.sidis.aircraft.common.security.JwtUtils;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceTemplateRepository;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MaintenanceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AircraftModelRepository modelRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private MaintenanceTemplateRepository templateRepository;

    @Autowired
    private pt.isep.sidis.aircraft.bootstrap.Bootstrapper bootstrapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        if (modelRepository.count() == 0) {
            bootstrapper.run();
        }
    }

    @Test
    void technicianCanCreateAndQueryMaintenanceTemplate() throws Exception {
        String techToken = jwtUtils.generateToken("tech1", "MAINTENANCE_TECHNICIAN");

        AircraftModel model = modelRepository.findByName("A320neo").orElseGet(() ->
                modelRepository.save(new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828)));

        CreateMaintenanceTemplateDTO dto = new CreateMaintenanceTemplateDTO(
                "Custom Hydraulic Inspection",
                "INSPECTION",
                12.0,
                List.of(model.getId()),
                List.of("Check main reservoir", "Test pressure accumulator")
        );

        mockMvc.perform(post("/api/maintenance-templates")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateName").value("Custom Hydraulic Inspection"))
                .andExpect(jsonPath("$.defaultDurationHours").value(12.0));

        // Supervisor can view templates
        String supervisorToken = jwtUtils.generateToken("sup1", "MAINTENANCE_SUPERVISOR");
        mockMvc.perform(get("/api/maintenance-templates")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void maintenanceRecordLifecycleRestoresAircraftToAvailable() throws Exception {
        String techToken = jwtUtils.generateToken("tech1", "MAINTENANCE_TECHNICIAN");

        AircraftModel a320 = modelRepository.findByName("A320neo").orElseGet(() ->
                modelRepository.save(new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828)));
        Aircraft testAircraft = aircraftRepository.save(new Aircraft("CS-TTEST", a320, LocalDate.of(2022, 1, 1), AircraftStatus.AVAILABLE));

        var template = templateRepository.findAll().stream().findFirst().orElseGet(() ->
                templateRepository.save(new pt.isep.sidis.aircraft.domain.MaintenanceTemplate(
                        "A-Check Routine Inspection",
                        pt.isep.sidis.aircraft.domain.TemplateType.INSPECTION,
                        8.0,
                        List.of(a320),
                        List.of("Check tyres", "Inspect cabin")
                )));

        CreateMaintenanceRecordDTO createDTO = new CreateMaintenanceRecordDTO(
                testAircraft.getRegistrationNumber(),
                template.getId(),
                "Integration test maintenance check",
                LocalDate.now(),
                10.0,
                "AIRFRAME",
                2500.0
        );

        // 1. Create record -> aircraft becomes UNDER_MAINTENANCE
        String responseBody = mockMvc.perform(post("/api/maintenance-records")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();

        Long recordId = objectMapper.readTree(responseBody).get("id").asLong();
        Long version = objectMapper.readTree(responseBody).get("version").asLong();

        // 2. Start record -> PLANNED -> IN_PROGRESS
        VersionedActionDTO startDTO = new VersionedActionDTO(version);
        String startResponse = mockMvc.perform(patch("/api/maintenance-records/" + recordId + "/start")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();

        Long updatedVersion = objectMapper.readTree(startResponse).get("version").asLong();

        // 3. Complete record -> IN_PROGRESS -> COMPLETED, aircraft restored to AVAILABLE
        CompleteMaintenanceRecordDTO completeDTO = new CompleteMaintenanceRecordDTO(
                "Checked and fully functional.",
                9.5,
                2400.0,
                updatedVersion
        );

        mockMvc.perform(patch("/api/maintenance-records/" + recordId + "/complete")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualDurationHours").value(9.5))
                .andExpect(jsonPath("$.actualCost").value(2400.0));

        // 4. Verify aircraft is back to AVAILABLE
        mockMvc.perform(get("/api/aircrafts/" + testAircraft.getRegistrationNumber())
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void reportsAccessibleByAuthorizedRoles() throws Exception {
        String atccToken = jwtUtils.generateToken("atccUser", "ATCC");
        String supToken = jwtUtils.generateToken("supUser", "MAINTENANCE_SUPERVISOR");

        mockMvc.perform(get("/api/maintenance-records/reports/total-hours")
                        .header("Authorization", "Bearer " + atccToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/maintenance-records/reports/cost-per-aircraft")
                        .header("Authorization", "Bearer " + atccToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/maintenance-records/ongoing")
                        .header("Authorization", "Bearer " + supToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/maintenance-records/reports/avg-turnaround")
                        .header("Authorization", "Bearer " + supToken))
                .andExpect(status().isOk());
    }
}
