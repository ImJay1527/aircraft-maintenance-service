package pt.isep.sidis.aircraft.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.api.dto.CreateMaintenanceTemplateDTO;
import pt.isep.sidis.aircraft.api.dto.UpdateMaintenanceTemplateDTO;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.services.MaintenanceTemplateService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"aircraft.db.name=template_controller_test", "aircraft.bootstrap.enabled=false"})
@AutoConfigureMockMvc
class MaintenanceTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceTemplateService templateService;

    private AircraftModel a320;
    private MaintenanceTemplate template;

    @BeforeEach
    void setUp() {
        a320 = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        a320.setId(1L);
        template = new MaintenanceTemplate("A-Check Routine", TemplateType.INSPECTION, 8.0, List.of(a320), List.of("Inspect engine"));
        template.setId(1L);
        template.setVersion(0L);
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void createTemplateReturns201Created() throws Exception {
        CreateMaintenanceTemplateDTO dto = new CreateMaintenanceTemplateDTO(
                "A-Check Routine", "INSPECTION", 8.0, List.of(1L), List.of("Inspect engine")
        );

        when(templateService.createTemplate(any(), any(), any(), any(), any())).thenReturn(template);

        mockMvc.perform(post("/api/maintenance-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.templateName").value("A-Check Routine"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_SUPERVISOR")
    void createTemplateForbiddenForSupervisor() throws Exception {
        CreateMaintenanceTemplateDTO dto = new CreateMaintenanceTemplateDTO(
                "A-Check Routine", "INSPECTION", 8.0, List.of(1L), List.of("Inspect engine")
        );

        mockMvc.perform(post("/api/maintenance-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void createTemplateInvalidBodyReturns400() throws Exception {
        // Missing templateName
        CreateMaintenanceTemplateDTO dto = new CreateMaintenanceTemplateDTO(
                "", "INSPECTION", 8.0, List.of(1L), List.of("Inspect engine")
        );

        mockMvc.perform(post("/api/maintenance-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_SUPERVISOR")
    void getAllTemplatesReturnsList() throws Exception {
        when(templateService.getAllTemplates()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/maintenance-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].templateName").value("A-Check Routine"));
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void getTemplateByIdReturns200WhenFound() throws Exception {
        when(templateService.getTemplateById(1L)).thenReturn(template);

        mockMvc.perform(get("/api/maintenance-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.templateName").value("A-Check Routine"));
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void getTemplateByIdReturns404WhenNotFound() throws Exception {
        when(templateService.getTemplateById(99L))
                .thenThrow(new ResourceNotFoundException("Maintenance template with id '99' not found."));

        mockMvc.perform(get("/api/maintenance-templates/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void updateTemplateReturns200OnSuccess() throws Exception {
        UpdateMaintenanceTemplateDTO dto = new UpdateMaintenanceTemplateDTO(
                "Updated Name", "OVERHAUL", 16.0, List.of(1L), List.of("Step 1"), 0L
        );

        when(templateService.updateTemplate(eq(1L), eq(0L), any(), any(), any(), any(), any()))
                .thenReturn(template);

        mockMvc.perform(patch("/api/maintenance-templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
