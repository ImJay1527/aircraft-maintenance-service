package pt.isep.sidis.aircraft.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.api.dto.CancelMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.CompleteMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.CreateMaintenanceRecordDTO;
import pt.isep.sidis.aircraft.api.dto.VersionedActionDTO;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceComponent;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;
import pt.isep.sidis.aircraft.domain.MaintenanceStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.services.MaintenanceRecordService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "aircraft.bootstrap.enabled=false")
@AutoConfigureMockMvc
class MaintenanceRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceRecordService recordService;

    private MaintenanceRecord record;

    @BeforeEach
    void setUp() {
        AircraftModel model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        Aircraft aircraft = new Aircraft("CS-TPA", model, LocalDate.of(2022, 1, 1), AircraftStatus.UNDER_MAINTENANCE);
        MaintenanceTemplate template = new MaintenanceTemplate("A-Check", TemplateType.INSPECTION, 8.0, List.of(model), List.of("Check step"));
        template.setId(1L);

        record = new MaintenanceRecord(aircraft, template, "Scheduled A-Check", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1500.0);
        record.setId(50L);
        record.setVersion(0L);
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void createRecordReturns201Created() throws Exception {
        CreateMaintenanceRecordDTO dto = new CreateMaintenanceRecordDTO(
                "CS-TPA", 1L, "Scheduled A-Check", LocalDate.now(), 8.0, "AIRFRAME", 1500.0
        );

        when(recordService.createRecord(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(record);

        mockMvc.perform(post("/api/maintenance-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void startRecordReturns200OnSuccess() throws Exception {
        record.startWork();
        when(recordService.startRecord(eq(50L), eq(0L))).thenReturn(record);

        VersionedActionDTO dto = new VersionedActionDTO(0L);

        mockMvc.perform(patch("/api/maintenance-records/50/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void startRecordOptimisticLockFailureReturns409Conflict() throws Exception {
        when(recordService.startRecord(eq(50L), eq(99L)))
                .thenThrow(new ObjectOptimisticLockingFailureException(MaintenanceRecord.class, 50L));

        VersionedActionDTO dto = new VersionedActionDTO(99L);

        mockMvc.perform(patch("/api/maintenance-records/50/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void completeRecordReturns200OnSuccess() throws Exception {
        record.startWork();
        record.markAsCompleted("Complete", 7.0, 1400.0);
        when(recordService.completeRecord(eq(50L), eq(0L), any(), any(), any()))
                .thenReturn(record);

        CompleteMaintenanceRecordDTO dto = new CompleteMaintenanceRecordDTO(
                "Complete", 7.0, 1400.0, 0L
        );

        mockMvc.perform(patch("/api/maintenance-records/50/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void cancelRecordReturns200OnSuccess() throws Exception {
        record.cancel("Cancelled by dispatcher");
        when(recordService.cancelRecord(eq(50L), eq(0L), any()))
                .thenReturn(record);

        CancelMaintenanceRecordDTO dto = new CancelMaintenanceRecordDTO(
                "Cancelled by dispatcher", 0L
        );

        mockMvc.perform(patch("/api/maintenance-records/50/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_SUPERVISOR")
    void getOngoingActivitiesReturnsList() throws Exception {
        when(recordService.getOngoingActivities()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/maintenance-records/ongoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ATCC")
    void getCostPerAircraftReportReturnsList() throws Exception {
        when(recordService.getCostReportPerAircraft())
                .thenReturn(List.of(new MaintenanceRecordService.MaintenanceCostSummary("CS-TPA", 1500.0, 1400.0)));

        mockMvc.perform(get("/api/maintenance-records/reports/cost-per-aircraft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("CS-TPA"))
                .andExpect(jsonPath("$[0].estimatedCost").value(1500.0));
    }

    @Test
    @WithMockUser(roles = "ATCC")
    void getTotalHoursReportReturnsList() throws Exception {
        when(recordService.getTotalMaintenanceHoursPerAircraft())
                .thenReturn(List.of(new MaintenanceRecordService.MaintenanceHoursSummary("CS-TPA", 50.0)));

        mockMvc.perform(get("/api/maintenance-records/reports/total-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registrationNumber").value("CS-TPA"))
                .andExpect(jsonPath("$[0].totalHours").value(50.0));
    }
}
