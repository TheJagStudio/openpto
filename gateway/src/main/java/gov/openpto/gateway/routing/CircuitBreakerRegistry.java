package gov.openpto.gateway.routing;

import gov.openpto.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One {@link DownstreamCircuitBreaker} per downstream service (shared by all routes to that service). */
@Component
public class CircuitBreakerRegistry {

    private final Map<String, DownstreamCircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final GatewayProperties.CircuitBreaker config;

    public CircuitBreakerRegistry(GatewayProperties properties) {
        this.config = properties.circuitBreaker();
    }

    public DownstreamCircuitBreaker forService(String service) {
        return breakers.computeIfAbsent(service,
                s -> new DownstreamCircuitBreaker(config.failureThreshold(), config.openDuration()));
    }
}
