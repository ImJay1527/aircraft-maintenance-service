package pt.isep.sidis.aircraft.common.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuditLogFilterTest {

    private AuditLogFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuditLogFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilterSkipsActuatorHealthEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        MockHttpServletRequest requestInfo = new MockHttpServletRequest("GET", "/actuator/info");
        assertThat(filter.shouldNotFilter(requestInfo)).isTrue();
    }

    @Test
    void shouldFilterNormalApiEndpoints() {
        MockHttpServletRequest reqApi = new MockHttpServletRequest("GET", "/api/aircrafts/CS-TPA");
        assertThat(filter.shouldNotFilter(reqApi)).isFalse();

        MockHttpServletRequest reqInternal = new MockHttpServletRequest("GET", "/internal/aircraft/CS-TPA");
        assertThat(filter.shouldNotFilter(reqInternal)).isFalse();
    }

    @Test
    void doFilterProcessesAnonymousRequestsWithoutException() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/aircrafts");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(401);

        assertDoesNotThrow(() -> filter.doFilter(req, res, (request, response) -> { }));
    }

    @Test
    void doFilterProcessesAuthenticatedPrincipal() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("technician1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_MAINTENANCE_TECHNICIAN")))
        );

        MockHttpServletRequest req = new MockHttpServletRequest("PATCH", "/api/maintenance-records/1/start");
        req.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(200);

        filter.doFilter(req, res, (request, response) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("technician1");
        });

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
