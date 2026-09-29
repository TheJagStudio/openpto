package gov.openpto.gateway.status;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.config.HttpClientConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Probes every downstream's {@code /actuator/health} in parallel on virtual threads (2s timeout each). */
@Service
public class GatewayStatusService {

    public record ServiceStatus(String name, String url, String status, long latencyMs) {
    }

    public record StatusResponse(List<ServiceStatus> services) {
    }

    private static final List<String> DISPLAY_ORDER =
            List.of(GatewayProperties.ODP, GatewayProperties.FEES, GatewayProperties.INGEST);

    private final GatewayProperties properties;
    private final HttpClient httpClient;
    private final Duration timeout;

    public GatewayStatusService(GatewayProperties properties) {
        this.properties = properties;
        this.timeout = properties.statusTimeout();
        this.httpClient = HttpClientConfig.jdkHttpClient(timeout);
    }

    public StatusResponse check() {
        List<Map.Entry<String, GatewayProperties.Service>> services = properties.services().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, GatewayProperties.Service> e) -> order(e.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .toList();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ServiceStatus>> futures = new ArrayList<>();
            for (Map.Entry<String, GatewayProperties.Service> e : services) {
                futures.add(executor.submit(() -> probe(e.getValue())));
            }
            List<ServiceStatus> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                results.add(await(futures.get(i), services.get(i).getValue()));
            }
            return new StatusResponse(results);
        }
    }

    private static int order(String serviceKey) {
        int i = DISPLAY_ORDER.indexOf(serviceKey);
        return i < 0 ? DISPLAY_ORDER.size() : i;
    }

    private ServiceStatus await(Future<ServiceStatus> future, GatewayProperties.Service service) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ServiceStatus(service.displayName(), service.url().toString(), "DOWN", -1);
        } catch (Exception e) {
            return new ServiceStatus(service.displayName(), service.url().toString(), "DOWN", -1);
        }
    }

    ServiceStatus probe(GatewayProperties.Service service) {
        URI health = UriComponentsBuilder.fromUri(service.url()).path("/actuator/health").build().toUri();
        long start = System.nanoTime();
        String status;
        try {
            HttpResponse<Void> response = httpClient.send(
                    HttpRequest.newBuilder(health).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            status = response.statusCode() / 100 == 2 ? "UP" : "DOWN";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = "DOWN";
        } catch (Exception e) {
            status = "DOWN";
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new ServiceStatus(service.displayName(), service.url().toString(), status, latencyMs);
    }
}
