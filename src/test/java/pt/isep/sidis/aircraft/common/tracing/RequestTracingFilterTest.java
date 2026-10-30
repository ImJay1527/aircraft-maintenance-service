package pt.isep.sidis.aircraft.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTracingFilterTest {

    private RequestTracingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTracingFilter("aircraft-node-1");
        MDC.clear();
    }

    @Test
    void existingRequestIdPreservedAndAddedToResponseAndMdc() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, "req-existing-999");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> mdcReqIdInChain = new AtomicReference<>();
        AtomicReference<String> mdcInstInChain = new AtomicReference<>();

        FilterChain chain = (request, response) -> {
            mdcReqIdInChain.set(MDC.get(RequestTracingFilter.MDC_REQUEST_ID));
            mdcInstInChain.set(MDC.get(RequestTracingFilter.MDC_INSTANCE));
        };

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)).isEqualTo("req-existing-999");
        assertThat(res.getHeader(RequestTracingFilter.INSTANCE_HEADER)).isEqualTo("aircraft-node-1");
        assertThat(mdcReqIdInChain.get()).isEqualTo("req-existing-999");
        assertThat(mdcInstInChain.get()).isEqualTo("aircraft-node-1");
    }

    @Test
    void missingRequestIdGeneratesNewId() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> mdcId = new AtomicReference<>();

        filter.doFilter(req, res, (request, response) -> mdcId.set(MDC.get(RequestTracingFilter.MDC_REQUEST_ID)));

        String headerId = res.getHeader(RequestTracingFilter.REQUEST_ID_HEADER);
        assertThat(headerId).isNotBlank().hasSize(8);
        assertThat(mdcId.get()).isEqualTo(headerId);
    }

    @Test
    void tooLongRequestIdRegenerated() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, "a".repeat(100)); // > 64 chars
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> { });

        String headerId = res.getHeader(RequestTracingFilter.REQUEST_ID_HEADER);
        assertThat(headerId).isNotBlank().hasSize(8).isNotEqualTo("a".repeat(100));
    }

    @Test
    void instanceHeaderAlwaysSetMatchingConfiguredName() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> { });

        assertThat(res.getHeader(RequestTracingFilter.INSTANCE_HEADER)).isEqualTo("aircraft-node-1");
    }

    @Test
    void mdcCleanedUpAfterFilterExecution() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> {
            assertThat(MDC.get(RequestTracingFilter.MDC_REQUEST_ID)).isNotNull();
            assertThat(MDC.get(RequestTracingFilter.MDC_INSTANCE)).isNotNull();
        });

        // MDC must be completely clean after filter finishes
        assertThat(MDC.get(RequestTracingFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestTracingFilter.MDC_INSTANCE)).isNull();
    }
}
