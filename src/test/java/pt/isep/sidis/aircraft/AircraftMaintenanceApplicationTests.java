package pt.isep.sidis.aircraft;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.common.security.JwtUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AircraftMaintenanceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unauthenticatedRequestToApiIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/aircrafts/CS-TPA"))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private pt.isep.sidis.aircraft.repositories.AircraftModelRepository modelRepository;

    @Autowired
    private pt.isep.sidis.aircraft.bootstrap.Bootstrapper bootstrapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        if (modelRepository.count() == 0) {
            bootstrapper.run();
        }
    }

    @Test
    void authenticatedUserCanCreateAndGetAircraft() throws Exception {
        String token = jwtUtils.generateToken("mechanic1", "AIRCRAFT_MECHANIC");

        var model = modelRepository.findByName("A320neo")
                .orElseGet(() -> modelRepository.save(new pt.isep.sidis.aircraft.domain.AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 850)));

        String aircraftJson = String.format("""
                {
                    "registrationNumber": "CS-TEST1",
                    "model": {
                        "id": %d
                    },
                    "manufacturingDate": "2023-01-15",
                    "status": "AVAILABLE"
                }
                """, model.getId());

        mockMvc.perform(post("/api/aircrafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aircraftJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TEST1"));

        mockMvc.perform(get("/api/aircrafts/CS-TEST1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TEST1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void bootstrappedAircraftCanBeFetchedByInternalEndpointWithServiceToken() throws Exception {
        String serviceToken = jwtUtils.generateServiceToken("flight-operations-service");

        mockMvc.perform(get("/internal/aircraft/CS-TPA")
                        .header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPA"))
                .andExpect(jsonPath("$.modelName").value("A320neo"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.maxRange").value(6300.0))
                .andExpect(jsonPath("$.fuelCapacity").value(24000.0))
                .andExpect(jsonPath("$.activeCapacity").value(160));
    }

    @Test
    void unknownAircraftReturns404() throws Exception {
        String serviceToken = jwtUtils.generateServiceToken("flight-operations-service");

        mockMvc.perform(get("/internal/aircraft/CS-XXX")
                        .header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void normalUserCannotAccessInternalEndpoints() throws Exception {
        String userToken = jwtUtils.generateToken("mechanic1", "AIRCRAFT_MECHANIC");

        mockMvc.perform(get("/internal/aircraft/CS-TPA")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
