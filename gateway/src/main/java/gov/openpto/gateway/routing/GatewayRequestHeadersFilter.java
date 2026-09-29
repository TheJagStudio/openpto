package gov.openpto.gateway.routing;

import gov.openpto.gateway.identity.ClientIpResolver;
import gov.openpto.gateway.web.GatewayHeaders;
import gov.openpto.gateway.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Last request-header filter before a request leaves the gateway:
 * <ul>
 *   <li>strips {@code X-Internal-Token} (only the gateway/services may use it) and {@code X-API-Key}
 *       (the secret stays at the edge);</li>
 *   <li>sets {@code X-Request-Id} to the id chosen by {@link RequestIdFilter};</li>
 *   <li>rebuilds {@code X-Forwarded-For/Proto/Host/Port}: inbound values are trusted only from a
 *       configured proxy, otherwise they are replaced with what the gateway observed.</li>
 * </ul>
 * Runs after the framework's {@code RemoveXForwardedRequestHeadersFilter} (order 0).
 */
public class GatewayRequestHeadersFilter implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    static final String X_FORWARDED_PORT = "X-Forwarded-Port";

    private final ClientIpResolver clientIpResolver;

    public GatewayRequestHeadersFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public int getOrder() {
        return 1_000;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        HttpHeaders out = new HttpHeaders();
        out.putAll(input);
        out.remove(GatewayHeaders.INTERNAL_TOKEN);
        out.remove(GatewayHeaders.API_KEY);

        HttpServletRequest servlet = request.servletRequest();
        Object requestId = servlet.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) {
            out.set(GatewayHeaders.REQUEST_ID, requestId.toString());
        }

        String remote = servlet.getRemoteAddr();
        boolean trustedPeer = clientIpResolver.isTrusted(remote);
        String inboundFor = servlet.getHeader(X_FORWARDED_FOR);
        out.set(X_FORWARDED_FOR, trustedPeer && inboundFor != null && !inboundFor.isBlank()
                ? inboundFor + ", " + remote : remote);
        out.set(X_FORWARDED_PROTO, pick(trustedPeer, servlet.getHeader(X_FORWARDED_PROTO), servlet.getScheme()));
        String host = servlet.getHeader(HttpHeaders.HOST);
        out.set(X_FORWARDED_HOST, pick(trustedPeer, servlet.getHeader(X_FORWARDED_HOST),
                host != null ? host : servlet.getServerName()));
        out.set(X_FORWARDED_PORT, pick(trustedPeer, servlet.getHeader(X_FORWARDED_PORT),
                Integer.toString(servlet.getServerPort())));
        return out;
    }

    private static String pick(boolean trustedPeer, String inbound, String observed) {
        return trustedPeer && inbound != null && !inbound.isBlank() ? inbound : observed;
    }
}
