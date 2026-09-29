package gov.openpto.gateway.web;

/** Header and query-parameter names used across the gateway. */
public final class GatewayHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";
    public static final String API_KEY = "X-API-Key";
    public static final String API_KEY_QUERY_PARAM = "api_key";
    public static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = "Retry-After";

    /** MDC key for the correlation id. */
    public static final String MDC_REQUEST_ID = "requestId";

    private GatewayHeaders() {
    }
}
