package pt.isep.sidis.aircraft.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.isep.sidis.aircraft.common.security.JwtUtils;

import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security integration tests.
 * Validates RBAC rules, JWT signature checking, service-to-service isolation,
 * and request tracing propagation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void publicApiWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/aircrafts/CS-TPA"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().exists("X-Instance"));
    }

    @Test
    void malformedBearerTokenIs401() throws Exception {
        mvc.perform(get("/api/aircrafts/CS-TPA").header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithForgedSignatureIs401() throws Exception {
        byte[] fakeSecret = "forged-secret-key-that-does-not-match-sidis-secret-123456".getBytes();
        String forgedToken = Jwts.builder()
                .setSubject("hacker")
                .claim("role", "ADMIN")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(fakeSecret), SignatureAlgorithm.HS256)
                .compact();

        mvc.perform(get("/api/aircrafts/CS-TPA").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenCannotCallInternalEndpoints() throws Exception {
        String supervisorToken = jwtUtils.generateToken("supervisor1", "MAINTENANCE_SUPERVISOR");
        mvc.perform(get("/internal/aircraft/CS-TPA").header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void atccTokenCannotCallInternalEndpoints() throws Exception {
        String atccToken = jwtUtils.generateToken("atcc_controller", "ATCC");
        mvc.perform(get("/internal/aircraft/CS-TPA").header("Authorization", "Bearer " + atccToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceTokenCanCallInternalEndpoints() throws Exception {
        String serviceToken = jwtUtils.generateServiceToken("flight-operations-service");
        mvc.perform(get("/internal/aircraft/CS-TPA").header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("CS-TPA"))
                .andExpect(header().string("X-Data-Source", "local"));
    }

    @Test
    void serviceTokenCannotCallUserEndpoints() throws Exception {
        String serviceToken = jwtUtils.generateServiceToken("flight-operations-service");
        mvc.perform(get("/api/maintenance-templates").header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void atccCanReadMaintenanceTemplates() throws Exception {
        String atccToken = jwtUtils.generateToken("atcc_controller", "ATCC");
        mvc.perform(get("/api/maintenance-templates").header("Authorization", "Bearer " + atccToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void atccCannotCreateMaintenanceTemplate() throws Exception {
        String atccToken = jwtUtils.generateToken("atcc_controller", "ATCC");
        mvc.perform(post("/api/maintenance-templates")
                        .header("Authorization", "Bearer " + atccToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateName\":\"New Template\",\"templateType\":\"INSPECTION\",\"defaultDurationHours\":4.0,\"applicableModelIds\":[1],\"checklistItems\":[\"Check\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCannotCreateMaintenanceTemplate() throws Exception {
        // According to US115A, template creation is restricted to MAINTENANCE_TECHNICIAN
        String supervisorToken = jwtUtils.generateToken("supervisor1", "MAINTENANCE_SUPERVISOR");
        mvc.perform(post("/api/maintenance-templates")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateName\":\"New Template\",\"templateType\":\"INSPECTION\",\"defaultDurationHours\":4.0,\"applicableModelIds\":[1],\"checklistItems\":[\"Check\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorCannotCancelMaintenanceRecord() throws Exception {
        // Record lifecycle operations are performed by MAINTENANCE_TECHNICIAN
        String supervisorToken = jwtUtils.generateToken("supervisor1", "MAINTENANCE_SUPERVISOR");
        mvc.perform(patch("/api/maintenance-records/1/cancel")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"reason\":\"Postponed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCanAccessTemplateCreationEndpoint() throws Exception {
        String techToken = jwtUtils.generateToken("technician1", "MAINTENANCE_TECHNICIAN");
        // Authorized role; 400 Bad Request verifies that authorization passed and Bean Validation executed
        mvc.perform(post("/api/maintenance-templates")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void technicianCannotAccessSupervisorReports() throws Exception {
        // US221 avg-turnaround report is restricted to MAINTENANCE_SUPERVISOR
        String techToken = jwtUtils.generateToken("technician1", "MAINTENANCE_TECHNICIAN");
        mvc.perform(get("/api/maintenance-records/reports/avg-turnaround")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void tracingHeadersPropagateIncomingRequestId() throws Exception {
        String supervisorToken = jwtUtils.generateToken("supervisor1", "MAINTENANCE_SUPERVISOR");
        mvc.perform(get("/api/aircrafts/CS-TPA")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .header("X-Request-Id", "trace-xyz-987"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-xyz-987"))
                .andExpect(header().string("X-Instance", "instance1"));
    }
}
