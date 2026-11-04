package pt.isep.sidis.aircraft.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodProjection;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UnicornsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AircraftMoodProjection moodProjection;

    @BeforeEach
    void setUp() {
        moodProjection.onStartup();
    }

    @Test
    void shouldReturnMoodOfAllAirplanes() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].registration", notNullValue()))
                .andExpect(jsonPath("$[0].mood", notNullValue()))
                .andExpect(jsonPath("$[0].energyLevel", notNullValue()))
                .andExpect(jsonPath("$[0].sparkleFactor", notNullValue()))
                .andExpect(jsonPath("$[0].statusMessage", notNullValue()));
    }

    @Test
    void shouldReturnMoodOfSpecificAircraft() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns/CS-TPA")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration", equalTo("CS-TPA")))
                .andExpect(jsonPath("$.mood", anyOf(equalTo("HAPPY"), equalTo("EXPERIENCED"))))
                .andExpect(jsonPath("$.energyLevel", greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.sparkleFactor", greaterThanOrEqualTo(4.0)));
    }

    @Test
    void shouldReturn404ForNonexistentAircraftMood() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns/CS-NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateMoodWhenStatusChangesViaEvent() throws Exception {
        // Given: event that aircraft enters maintenance
        moodProjection.onAircraftStatusChanged(new AircraftStatusChangedEvent(
                "CS-TPA", "AVAILABLE", "UNDER_MAINTENANCE", 150.0
        ));

        // When/Then: mood is SLEEPY
        mockMvc.perform(get("/api/v2/unicorns/CS-TPA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood", equalTo("SLEEPY")))
                .andExpect(jsonPath("$.energyLevel", equalTo(40)))
                .andExpect(jsonPath("$.statusMessage", containsString("hangar")));
    }
}
