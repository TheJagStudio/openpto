package gov.openpto.gateway.ratelimit;

import gov.openpto.gateway.web.GatewayHeaders;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Outcome of one token consumption.
 *
 * @param allowed           whether the request may proceed
 * @param limit             per-minute limit ({@code X-RateLimit-Limit})
 * @param remaining         tokens left across minute and day windows ({@code X-RateLimit-Remaining})
 * @param resetSeconds      seconds until the minute window is full again ({@code X-RateLimit-Reset})
 * @param retryAfterSeconds seconds to wait before retrying; 0 when allowed ({@code Retry-After})
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, long resetSeconds,
                                long retryAfterSeconds) {

    public void applyHeaders(HttpServletResponse response) {
        response.setHeader(GatewayHeaders.RATE_LIMIT_LIMIT, Long.toString(limit));
        response.setHeader(GatewayHeaders.RATE_LIMIT_REMAINING, Long.toString(remaining));
        response.setHeader(GatewayHeaders.RATE_LIMIT_RESET, Long.toString(resetSeconds));
        if (!allowed) {
            response.setHeader(GatewayHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
    }
}
