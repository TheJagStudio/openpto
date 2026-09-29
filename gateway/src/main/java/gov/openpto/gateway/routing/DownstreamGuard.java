package gov.openpto.gateway.routing;

import gov.openpto.gateway.web.GatewayHeaders;
import gov.openpto.gateway.web.ProblemResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

/**
 * Route filter around the proxy call: fails fast (503) while the service's circuit is open and turns
 * transport errors into ProblemDetails naming the service: connect failures → 502, timeouts → 504.
 */
public class DownstreamGuard implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(DownstreamGuard.class);

    private final String serviceName;
    private final DownstreamCircuitBreaker breaker;

    public DownstreamGuard(String serviceName, DownstreamCircuitBreaker breaker) {
        this.serviceName = serviceName;
        this.breaker = breaker;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (!breaker.allowRequest()) {
            long retryAfter = breaker.retryAfterSeconds();
            return ProblemResponses.serverResponse(request, HttpStatus.SERVICE_UNAVAILABLE,
                    serviceName + " is temporarily unavailable after repeated failures. Retry in "
                            + retryAfter + " seconds.",
                    Map.of(GatewayHeaders.RETRY_AFTER, Long.toString(retryAfter)));
        }
        try {
            ServerResponse response = next.handle(request);
            breaker.onSuccess();
            return response;
        } catch (ResourceAccessException e) {
            breaker.onFailure();
            boolean timeout = isTimeout(e);
            log.warn("{} {} -> {} {}: {}", request.method(), request.path(), serviceName,
                    timeout ? "timed out" : "unreachable", rootMessage(e));
            if (timeout) {
                return ProblemResponses.serverResponse(request, HttpStatus.GATEWAY_TIMEOUT,
                        serviceName + " did not respond in time.");
            }
            return ProblemResponses.serverResponse(request, HttpStatus.BAD_GATEWAY,
                    serviceName + " is unavailable. Please try again shortly.");
        }
    }

    static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
