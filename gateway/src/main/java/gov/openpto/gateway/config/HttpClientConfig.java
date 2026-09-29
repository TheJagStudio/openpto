package gov.openpto.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * HTTP client for the gateway's own calls to downstreams (key verification, usage flush, api-docs).
 * Proxied traffic uses per-route clients built in {@link ProxyConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    /** JDK HttpClient pinned to HTTP/1.1 (no h2c upgrade dance) running on virtual threads. */
    public static HttpClient jdkHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Bean
    RestClient serviceRestClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkHttpClient(Duration.ofSeconds(2)));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
