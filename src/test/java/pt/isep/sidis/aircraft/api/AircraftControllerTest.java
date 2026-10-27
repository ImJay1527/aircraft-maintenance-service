package pt.isep.sidis.aircraft.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.services.AircraftService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"aircraft.db.name=aircraft_controller_test", "aircraft.bootstrap.enabled=false"})
@AutoConfigureMockMvc
class AircraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AircraftService aircraftService;

    @Test
    void unauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/aircrafts/CS-TPA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AIRCRAFT_MECHANIC")
    void getAircraftReturns200WhenFound() throws Exception {
        AircraftModel model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        Aircraft aircraft = new Aircraft("CS-TPA", model, LocalDate.of(2023, 1, 1), AircraftStatus.AVAILABLE);

        when(aircraftService.getAircraft("CS-TPA")).thenReturn(aircraft);

        mockMvc.perform(get("/api/aircrafts/CS-TPA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPA"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "AIRCRAFT_MECHANIC")
    void getAircraftReturns404WhenNotFound() throws Exception {
        when(aircraftService.getAircraft("CS-UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Aircraft not found with registration: CS-UNKNOWN"));

        mockMvc.perform(get("/api/aircrafts/CS-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Aircraft not found with registration: CS-UNKNOWN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAircraftReturns200OnSuccess() throws Exception {
        AircraftModel model = new AircraftModel("737 MAX", "Boeing", 180, 26000, 6500, 840);
        Aircraft aircraft = new Aircraft("CS-TPB", model, LocalDate.now(), AircraftStatus.AVAILABLE);

        when(aircraftService.createAircraft(any(Aircraft.class))).thenReturn(aircraft);

        mockMvc.perform(post("/api/aircrafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aircraft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPB"));
    }
}
