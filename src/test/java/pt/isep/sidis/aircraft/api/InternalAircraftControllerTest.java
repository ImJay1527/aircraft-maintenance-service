package pt.isep.sidis.aircraft.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.internal.AircraftInfo;
import pt.isep.sidis.aircraft.replication.PeerClient;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "aircraft.bootstrap.enabled=false")
@AutoConfigureMockMvc
class InternalAircraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AircraftRepository aircraftRepository;

    @MockBean
    private PeerClient peerClient;

    private Aircraft localAircraft;

    @BeforeEach
    void setUp() {
        AircraftModel model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        localAircraft = new Aircraft("CS-TPA", model, LocalDate.of(2023, 1, 1), AircraftStatus.AVAILABLE);
    }

    @Test
    void unauthenticatedCallToInternalReturns401() throws Exception {
        mockMvc.perform(get("/internal/aircraft/CS-TPA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AIRCRAFT_MECHANIC")
    void userRoleCallToInternalReturns403Forbidden() throws Exception {
        mockMvc.perform(get("/internal/aircraft/CS-TPA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void localHitReturns200WithLocalProvenanceHeader() throws Exception {
        when(aircraftRepository.findByRegistrationNumber("CS-TPA")).thenReturn(Optional.of(localAircraft));

        mockMvc.perform(get("/internal/aircraft/CS-TPA"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "local"))
                .andExpect(header().exists("X-Instance"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPA"))
                .andExpect(jsonPath("$.modelName").value("A320neo"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.maxRange").value(6300.0))
                .andExpect(jsonPath("$.fuelCapacity").value(24000.0))
                .andExpect(jsonPath("$.activeCapacity").value(160));

        verify(peerClient, never()).fetchAircraftFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void localMissForwardsToPeerAndReturns200WithPeerProvenanceHeader() throws Exception {
        when(aircraftRepository.findByRegistrationNumber("CS-TPX")).thenReturn(Optional.empty());

        AircraftInfo remoteInfo = new AircraftInfo(
                "CS-TPX", "AVAILABLE", "A320neo", 6300.0, 24000.0, 160
        );
        when(peerClient.fetchAircraftFromPeers(eq("CS-TPX"), any())).thenReturn(Optional.of(remoteInfo));

        mockMvc.perform(get("/internal/aircraft/CS-TPX")
                        .header("X-Request-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "peer"))
                .andExpect(header().string("X-Request-Id", "trace-123"))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPX"));

        verify(peerClient, times(1)).fetchAircraftFromPeers(eq("CS-TPX"), eq("trace-123"));
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void loopPreventionHeaderStopsForwardingOnLocalMiss() throws Exception {
        when(aircraftRepository.findByRegistrationNumber("CS-UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/aircraft/CS-UNKNOWN")
                        .header("X-Forwarded-From", "instance2"))
                .andExpect(status().isNotFound());

        // Verifies peer was NEVER contacted because loop prevention cut the storm!
        verify(peerClient, never()).fetchAircraftFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void localMissAndPeerMissReturns404() throws Exception {
        when(aircraftRepository.findByRegistrationNumber("CS-NONEXISTENT")).thenReturn(Optional.empty());
        when(peerClient.fetchAircraftFromPeers(eq("CS-NONEXISTENT"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/aircraft/CS-NONEXISTENT"))
                .andExpect(status().isNotFound());

        verify(peerClient, times(1)).fetchAircraftFromPeers(eq("CS-NONEXISTENT"), any());
    }
}
