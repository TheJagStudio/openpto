package gov.openpto.gateway.routing;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.web.GatewayHeaders;
import gov.openpto.gateway.web.ProblemResponses;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.function.Function;

/**
 * Route table (Java DSL). Order matters only for {@code /internal/**}, which is matched first and always
 * answers 404 so internal endpoints can never be reached through the gateway.
 * <p>Per proxied route: {@code uri(service)} → {@code preserveHostHeader} → strip {@code api_key} param
 * (data routes) → {@link DownstreamGuard} (circuit breaker + 502/503/504 ProblemDetails) → {@code http()}.
 */
@Configuration(proxyBeanMethods = false)
public class RoutesConfig {

    @Bean
    RouterFunction<ServerResponse> gatewayRoutes(GatewayProperties properties, CircuitBreakerRegistry breakers) {
        RouterFunction<ServerResponse> routes = GatewayRouterFunctions.route("internal-blocked")
                .route(anyPath(RoutePaths.INTERNAL), request ->
                        ProblemResponses.serverResponse(request, HttpStatus.NOT_FOUND, "No route for this path."))
                .build();
        for (RouteSpec spec : RouteSpec.ALL) {
            routes = routes.and(proxyRoute(spec, properties, breakers));
        }
        return routes;
    }

    static RouterFunction<ServerResponse> proxyRoute(RouteSpec spec, GatewayProperties properties,
                                                     CircuitBreakerRegistry breakers) {
        GatewayProperties.Service service = properties.service(spec.service());
        RouterFunctions.Builder builder = GatewayRouterFunctions.route(spec.id())
                .route(anyPath(spec.paths()), HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(service.url()))
                .before(BeforeFilterFunctions.preserveHostHeader());
        if (spec.stripApiKey()) {
            builder = builder.before(stripApiKeyParam());
        }
        return builder
                .filter(new DownstreamGuard(service.displayName(), breakers.forService(spec.service())))
                .build();
    }

    /** Removes {@code api_key} from the forwarded query only when present (no request rebuild otherwise). */
    static Function<ServerRequest, ServerRequest> stripApiKeyParam() {
        Function<ServerRequest, ServerRequest> remove =
                BeforeFilterFunctions.removeRequestParameter(GatewayHeaders.API_KEY_QUERY_PARAM);
        return request -> {
            String query = request.servletRequest().getQueryString();
            return query != null && query.contains(GatewayHeaders.API_KEY_QUERY_PARAM) ? remove.apply(request) : request;
        };
    }

    static RequestPredicate anyPath(List<String> patterns) {
        RequestPredicate predicate = RequestPredicates.path(patterns.getFirst());
        for (String pattern : patterns.subList(1, patterns.size())) {
            predicate = predicate.or(RequestPredicates.path(pattern));
        }
        return predicate;
    }
}
