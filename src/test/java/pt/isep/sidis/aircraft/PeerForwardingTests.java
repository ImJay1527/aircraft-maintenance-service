package pt.isep.sidis.aircraft;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.internal.AircraftInfo;
import pt.isep.sidis.aircraft.replication.PeerClient;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PeerForwardingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PeerClient peerClient;

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalHitDoesNotForwardToPeer() throws Exception {
        // CS-TPA is bootstrapped locally
        mockMvc.perform(get("/internal/aircraft/CS-TPA"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "local"))
                .andExpect(header().string("X-Instance", "instance1"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPA"));

        // Verify peer was NOT contacted
        verify(peerClient, never()).fetchAircraftFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalMissForwardsToPeerAndReturnsAggregatedResult() throws Exception {
        // CS-TPX is not in local database (for instance1), but peer has it
        AircraftInfo remoteAircraft = new AircraftInfo(
                "CS-TPX", "AVAILABLE", "A320neo", 6300.0, 24000.0, 160
        );
        when(peerClient.fetchAircraftFromPeers(eq("CS-TPX"), any())).thenReturn(Optional.of(remoteAircraft));

        mockMvc.perform(get("/internal/aircraft/CS-TPX"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "peer"))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPX"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.modelName").value("A320neo"));

        // Verify peer was contacted
        verify(peerClient, times(1)).fetchAircraftFromPeers(eq("CS-TPX"), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLoopPreventionWhenAlreadyForwarded() throws Exception {
        // Request contains X-Forwarded-From header -> Must NOT forward again!
        mockMvc.perform(get("/internal/aircraft/CS-UNKNOWN")
                        .header("X-Forwarded-From", "instance2"))
                .andExpect(status().isNotFound());

        // Verify peer was NEVER contacted because of loop prevention
        verify(peerClient, never()).fetchAircraftFromPeers(any(), any());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void testLocalMissAndPeerMissReturnsNotFound() throws Exception {
        when(peerClient.fetchAircraftFromPeers(eq("CS-NONEXISTENT"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/aircraft/CS-NONEXISTENT"))
                .andExpect(status().isNotFound());

        verify(peerClient, times(1)).fetchAircraftFromPeers(eq("CS-NONEXISTENT"), any());
    }
}
