package gov.openpto.gateway.web;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.identity.ClientIpResolver;
import gov.openpto.gateway.identity.Identity;
import gov.openpto.gateway.identity.IdentityResolver;
import gov.openpto.gateway.identity.InvalidCredentialsException;
import gov.openpto.gateway.identity.UpstreamUnavailableException;
import gov.openpto.gateway.ratelimit.RateLimitDecision;
import gov.openpto.gateway.ratelimit.RateLimiterService;
import gov.openpto.gateway.routing.RoutePaths;
import gov.openpto.gateway.usage.UsageMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Identity resolution + throttling, runs after Spring Security (so CORS preflights are answered first)
 * and before the router.
 * <ul>
 *   <li>Tiered routes (data + fees): resolve {@link Identity} → 401 on invalid key/JWT, 502 if the key
 *       verifier is down → consume from the identity's bucket → 429 when empty; usage is metered per key.</li>
 *   <li>POST /api/v1/auth/login|register: strict per-IP bucket (default 10/min, no daily quota).</li>
 * </ul>
 * Every limited response carries {@code X-RateLimit-Limit/Remaining/Reset}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final IdentityResolver identityResolver;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiter;
    private final UsageMeter usageMeter;
    private final ProblemResponses problems;
    private final GatewayProperties.RateLimit config;

    public RateLimitFilter(IdentityResolver identityResolver, ClientIpResolver clientIpResolver,
                           RateLimiterService rateLimiter, UsageMeter usageMeter, ProblemResponses problems,
                           GatewayProperties properties) {
        this.identityResolver = identityResolver;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
        this.usageMeter = usageMeter;
        this.problems = problems;
        this.config = properties.rateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (RoutePaths.isTiered(request)) {
            if (!handleTiered(request, response)) {
                return;
            }
        } else if (RoutePaths.isAuthLimited(request) && config.enabled()) {
            String ip = clientIpResolver.resolve(request);
            RateLimitDecision decision = rateLimiter.tryConsume("auth-ip:" + ip, config.authPerMinute(), 0);
            decision.applyHeaders(response);
            if (!decision.allowed()) {
                reject(request, response, decision, "Too many sign-in attempts from this address.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** @return true when the request may continue */
    private boolean handleTiered(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Identity identity;
        try {
            identity = identityResolver.resolve(request);
        } catch (InvalidCredentialsException e) {
            if (e.isBearer()) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            }
            problems.write(request, response, HttpStatus.UNAUTHORIZED, e.getMessage());
            return false;
        } catch (UpstreamUnavailableException e) {
            log.warn("Identity resolution failed: {} ({})", e.getMessage(), e.serviceName());
            problems.write(request, response, HttpStatus.BAD_GATEWAY,
                    "Could not verify credentials because " + e.serviceName() + " is unavailable. Try again shortly.");
            return false;
        }
        request.setAttribute(Identity.ATTRIBUTE, identity);
        if (!config.enabled()) {
            return true;
        }
        RateLimitDecision decision = rateLimiter.tryConsume(identity.bucketKey(), identity.perMinute(),
                identity.perDay());
        decision.applyHeaders(response);
        if (!decision.allowed()) {
            reject(request, response, decision, "Rate limit exceeded for tier " + identity.tier()
                    + " (" + identity.perMinute() + "/min, " + identity.perDay() + "/day).");
            return false;
        }
        if (identity.type() == Identity.Type.KEY) {
            usageMeter.record(identity.id());
        }
        return true;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision,
                        String message) throws IOException {
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                message + " Retry after " + decision.retryAfterSeconds() + " seconds.");
    }
}
