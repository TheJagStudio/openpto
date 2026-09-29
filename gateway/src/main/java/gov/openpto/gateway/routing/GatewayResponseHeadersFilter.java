package gov.openpto.gateway.routing;

import gov.openpto.gateway.web.GatewayHeaders;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Locale;

/**
 * Drops downstream headers the gateway owns, so they are not duplicated on the client response:
 * {@code X-Request-Id} (already set by the gateway), CORS headers (CORS lives only at the gateway) and
 * any {@code X-RateLimit-*} a downstream might emit.
 */
public class GatewayResponseHeadersFilter implements HttpHeadersFilter.ResponseHttpHeadersFilter, Ordered {

    @Override
    public int getOrder() {
        return 1_000;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerResponse response) {
        HttpHeaders out = new HttpHeaders();
        input.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals(GatewayHeaders.REQUEST_ID.toLowerCase(Locale.ROOT))
                    && !lower.startsWith("access-control-")
                    && !lower.startsWith("x-ratelimit-")) {
                out.addAll(name, values);
            }
        });
        return out;
    }
}
