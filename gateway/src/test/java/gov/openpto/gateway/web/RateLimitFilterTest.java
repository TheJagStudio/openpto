package gov.openpto.gateway.web;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.identity.ClientIpResolver;
import gov.openpto.gateway.identity.Identity;
import gov.openpto.gateway.identity.IdentityResolver;
import gov.openpto.gateway.identity.Tier;
import gov.openpto.gateway.identity.UpstreamUnavailableException;
import gov.openpto.gateway.ratelimit.RateLimiterService;
import gov.openpto.gateway.usage.UsageMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    IdentityResolver identityResolver;
    @Mock
    UsageMeter usageMeter;

    final ProblemResponses problems = new ProblemResponses(JsonMapper.builder().build());

    RateLimitFilter filter(boolean enabled) {
        GatewayProperties properties = new GatewayProperties(
                Map.of("odp", new GatewayProperties.Service("odp-service", URI.create("http://odp"), null, null)),
                null, "t", List.of(), null, null, null,
                new GatewayProperties.RateLimit(enabled, null, 2, null, 0), null, null, null, null);
        return new RateLimitFilter(identityResolver, new ClientIpResolver(properties),
                new RateLimiterService(properties), usageMeter, problems, properties);
    }

    @Test
    void verifierDown_is502ProblemNamingOdp() throws Exception {
        when(identityResolver.resolve(any())).thenThrow(new UpstreamUnavailableException("odp-service", "down", null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(true).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("odp-service");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void disabledLimiter_stillResolvesIdentity_butSetsNoHeaders() throws Exception {
        when(identityResolver.resolve(any())).thenReturn(new Identity(Identity.Type.KEY, "k", Tier.FREE, 1, 1));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fees/schedules");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(false).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(Identity.ATTRIBUTE)).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        verify(usageMeter, never()).record(any());
    }

    @Test
    void untieredPaths_andPreflight_passStraightThrough() throws Exception {
        RateLimitFilter filter = filter(true);
        for (MockHttpServletRequest request : List.of(
                new MockHttpServletRequest("GET", "/api/v1/account/api-keys"),
                new MockHttpServletRequest("GET", "/api/v1/auth/login"),
                new MockHttpServletRequest("OPTIONS", "/api/v1/patents"))) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        }
        verifyNoInteractions(identityResolver);
    }

    @Test
    void keyUsage_isMeteredOnlyWhenAllowed() throws Exception {
        when(identityResolver.resolve(any())).thenReturn(new Identity(Identity.Type.KEY, "k9", Tier.FREE, 1, 10));
        RateLimitFilter filter = filter(true);

        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/stats"), ok, new MockFilterChain());
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/stats"), limited, new MockFilterChain());

        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        verify(usageMeter).record("k9");
    }
}
