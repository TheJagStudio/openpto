package gov.openpto.gateway.routing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamGuardTest {

    final AtomicLong nanos = new AtomicLong();
    final DownstreamCircuitBreaker breaker = new DownstreamCircuitBreaker(2, Duration.ofSeconds(10), nanos::get);
    final DownstreamGuard guard = new DownstreamGuard("fee-service", breaker);
    final ServerRequest request = ServerRequest.create(new MockHttpServletRequest("GET", "/api/v1/fees/x"), List.of());

    @Test
    void success_passesThroughAndClosesCircuit() throws Exception {
        breaker.onFailure();
        ServerResponse response = guard.filter(request, r -> ServerResponse.ok().build());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(breaker.state()).isEqualTo(DownstreamCircuitBreaker.State.CLOSED);
    }

    @Test
    void connectFailure_is502_timeout_is504() throws Exception {
        ServerResponse refused = guard.filter(request, r -> {
            throw new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
        });
        assertThat(refused.statusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        breaker.onSuccess();
        ServerResponse slow = guard.filter(request, r -> {
            throw new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out"));
        });
        assertThat(slow.statusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void repeatedFailures_openCircuit_failFastWith503_thenHalfOpen() throws Exception {
        for (int i = 0; i < 2; i++) {
            guard.filter(request, r -> {
                throw new ResourceAccessException("down", new ConnectException());
            });
        }
        assertThat(breaker.state()).isEqualTo(DownstreamCircuitBreaker.State.OPEN);

        ServerResponse fast = guard.filter(request, r -> {
            throw new AssertionError("must not call downstream while open");
        });
        assertThat(fast.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(fast.headers().getFirst("Retry-After")).isEqualTo("10");

        nanos.addAndGet(Duration.ofSeconds(10).toNanos());
        assertThat(breaker.state()).isEqualTo(DownstreamCircuitBreaker.State.HALF_OPEN);
        assertThat(guard.filter(request, r -> ServerResponse.ok().build()).statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(breaker.state()).isEqualTo(DownstreamCircuitBreaker.State.CLOSED);
    }

    @Test
    void halfOpenFailure_reopensImmediately() {
        breaker.onFailure();
        breaker.onFailure();
        nanos.addAndGet(Duration.ofSeconds(11).toNanos());
        assertThat(breaker.allowRequest()).isTrue();
        breaker.onFailure();
        assertThat(breaker.state()).isEqualTo(DownstreamCircuitBreaker.State.OPEN);
        assertThat(breaker.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    void isTimeout_classifiesCauses() {
        assertThat(DownstreamGuard.isTimeout(new ResourceAccessException("x", new HttpTimeoutException("t")))).isTrue();
        assertThat(DownstreamGuard.isTimeout(new ResourceAccessException("x",
                new java.net.SocketTimeoutException("t")))).isTrue();
        assertThat(DownstreamGuard.isTimeout(new ResourceAccessException("x",
                new HttpConnectTimeoutException("c")))).isFalse();
        assertThat(DownstreamGuard.isTimeout(new ResourceAccessException("x", new IOException("x")))).isFalse();
    }
}
