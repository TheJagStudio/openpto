package gov.openpto.gateway.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void resolve_keepsSafeIds_replacesUnsafeOnes() {
        assertThat(RequestIdFilter.resolve("abc-123_45.67:89")).isEqualTo("abc-123_45.67:89");
        assertThat(UUID.fromString(RequestIdFilter.resolve(null))).isNotNull();
        assertThat(UUID.fromString(RequestIdFilter.resolve("x\r\ninjected: 1"))).isNotNull();
        assertThat(UUID.fromString(RequestIdFilter.resolve("short"))).isNotNull();
        assertThat(UUID.fromString(RequestIdFilter.resolve("a".repeat(200)))).isNotNull();
    }

    @Test
    void filter_setsAttributeMdcAndResponseHeader_thenClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stats");
        request.addHeader("X-Request-Id", "client-supplied-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuring = new AtomicReference<>();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuring.set(MDC.get(GatewayHeaders.MDC_REQUEST_ID));
            }
        });

        assertThat(mdcDuring.get()).isEqualTo("client-supplied-1");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo("client-supplied-1");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-supplied-1");
        assertThat(MDC.get(GatewayHeaders.MDC_REQUEST_ID)).isNull();
    }
}
