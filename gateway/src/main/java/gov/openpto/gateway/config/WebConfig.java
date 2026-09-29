package gov.openpto.gateway.config;

import gov.openpto.gateway.identity.ClientIpResolver;
import gov.openpto.gateway.identity.IdentityResolver;
import gov.openpto.gateway.ratelimit.RateLimiterService;
import gov.openpto.gateway.usage.UsageMeter;
import gov.openpto.gateway.web.ProblemResponses;
import gov.openpto.gateway.web.RateLimitFilter;
import gov.openpto.gateway.web.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Servlet filter order (lowest runs first):
 * <ol>
 *   <li>{@link RequestIdFilter} — HIGHEST_PRECEDENCE + 10: correlation id, MDC, access log</li>
 *   <li>Spring Security — -100: firewall, CORS, security headers, JWT on ingest</li>
 *   <li>{@link RateLimitFilter} — 0: identity resolution, throttling, usage metering</li>
 *   <li>DispatcherServlet → gateway {@code RouterFunction}s → per-route {@code DownstreamGuard} → proxy</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig {

    public static final int REQUEST_ID_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int RATE_LIMIT_FILTER_ORDER = 0;

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(REQUEST_ID_FILTER_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(IdentityResolver identityResolver,
                                                            ClientIpResolver clientIpResolver,
                                                            RateLimiterService rateLimiter, UsageMeter usageMeter,
                                                            ProblemResponses problems, GatewayProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(
                identityResolver, clientIpResolver, rateLimiter, usageMeter, problems, properties));
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        return registration;
    }
}
