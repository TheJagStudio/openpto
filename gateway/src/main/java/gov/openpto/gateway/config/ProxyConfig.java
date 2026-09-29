package gov.openpto.gateway.config;

import gov.openpto.gateway.identity.ClientIpResolver;
import gov.openpto.gateway.routing.GatewayRequestHeadersFilter;
import gov.openpto.gateway.routing.GatewayResponseHeadersFilter;
import gov.openpto.gateway.routing.RouteAwareProxyExchange;
import gov.openpto.gateway.routing.RouteSpec;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy plumbing: per-route HTTP clients (timeouts) and the gateway's own request/response header filters.
 * Defining a {@link ProxyExchange} bean makes the framework's default {@code RestClientProxyExchange} back off.
 */
@Configuration(proxyBeanMethods = false)
public class ProxyConfig {

    @Bean
    ProxyExchange routeAwareProxyExchange(GatewayProperties properties, GatewayMvcProperties mvcProperties) {
        Map<String, ProxyExchange> byRoute = new HashMap<>();
        for (RouteSpec spec : RouteSpec.ALL) {
            GatewayProperties.Service service = properties.service(spec.service());
            Duration readTimeout = properties.routeReadTimeouts().getOrDefault(spec.id(), service.readTimeout());
            byRoute.put(spec.id(), exchange(service.connectTimeout(), readTimeout, mvcProperties));
        }
        ProxyExchange fallback = exchange(Duration.ofSeconds(2), Duration.ofSeconds(30), mvcProperties);
        return new RouteAwareProxyExchange(byRoute, fallback);
    }

    private static ProxyExchange exchange(Duration connect, Duration read, GatewayMvcProperties mvcProperties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClientConfig.jdkHttpClient(connect));
        factory.setReadTimeout(read);
        return new RestClientProxyExchange(RestClient.builder().requestFactory(factory).build(), mvcProperties);
    }

    @Bean
    GatewayRequestHeadersFilter gatewayRequestHeadersFilter(ClientIpResolver clientIpResolver) {
        return new GatewayRequestHeadersFilter(clientIpResolver);
    }

    @Bean
    GatewayResponseHeadersFilter gatewayResponseHeadersFilter() {
        return new GatewayResponseHeadersFilter();
    }
}
