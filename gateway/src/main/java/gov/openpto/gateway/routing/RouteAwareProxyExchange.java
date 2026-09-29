package gov.openpto.gateway.routing;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Map;

/**
 * Replaces the gateway's single shared {@code RestClientProxyExchange} with one exchange per route, each
 * backed by its own JDK HttpClient, so connect/read timeouts can differ per route (e.g. long CSV exports
 * and uploads vs. snappy search). Bodies stream in both directions; nothing is buffered here.
 */
public class RouteAwareProxyExchange implements ProxyExchange {

    private final Map<String, ProxyExchange> byRouteId;
    private final ProxyExchange fallback;

    public RouteAwareProxyExchange(Map<String, ProxyExchange> byRouteId, ProxyExchange fallback) {
        this.byRouteId = Map.copyOf(byRouteId);
        this.fallback = fallback;
    }

    @Override
    public ServerResponse exchange(Request request) {
        Object routeId = request.getServerRequest().attribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR).orElse(null);
        ProxyExchange delegate = routeId == null ? fallback : byRouteId.getOrDefault(routeId.toString(), fallback);
        return delegate.exchange(request);
    }
}
