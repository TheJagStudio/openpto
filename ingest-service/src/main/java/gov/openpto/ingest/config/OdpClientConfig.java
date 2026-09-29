package gov.openpto.ingest.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OdpClientConfig {

    @Bean
    RestClient odpRestClient(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Odp odp = properties.odp();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(odp.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(odp.readTimeout());
        return builder.clone()
                .baseUrl(odp.baseUrl())
                .requestFactory(factory)
                .build();
    }
}