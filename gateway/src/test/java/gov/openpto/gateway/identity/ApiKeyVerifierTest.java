package gov.openpto.gateway.identity;

import com.github.benmanes.caffeine.cache.Ticker;
import gov.openpto.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyVerifierTest {

    static final String KEY = "opto_" + "a1B2c3D4e5".repeat(4);
    static final ApiKeyVerification VALID = new ApiKeyVerification(true, "k1", "u1", "FREE", 120L, 20_000L);

    @Mock
    ApiKeyVerificationClient client;

    final AtomicLong nanos = new AtomicLong();
    final Ticker ticker = nanos::get;

    ApiKeyVerifier verifier() {
        return new ApiKeyVerifier(client, new GatewayProperties.ApiKeys(null, null, 0), ticker);
    }

    void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    void verify_validKey_callsDownstreamOnceWithin60s() {
        when(client.verify(KEY)).thenReturn(VALID);
        ApiKeyVerifier verifier = verifier();

        assertThat(verifier.verify(KEY)).isEqualTo(VALID);
        advance(Duration.ofSeconds(59));
        assertThat(verifier.verify(KEY)).isEqualTo(VALID);
        verify(client, times(1)).verify(KEY);

        advance(Duration.ofSeconds(2));
        verifier.verify(KEY);
        verify(client, times(2)).verify(KEY);
    }

    @Test
    void verify_invalidKey_isCachedOnly10s() {
        when(client.verify(KEY)).thenReturn(ApiKeyVerification.invalid());
        ApiKeyVerifier verifier = verifier();

        assertThat(verifier.verify(KEY).valid()).isFalse();
        advance(Duration.ofSeconds(9));
        verifier.verify(KEY);
        verify(client, times(1)).verify(KEY);

        advance(Duration.ofSeconds(2));
        verifier.verify(KEY);
        verify(client, times(2)).verify(KEY);
    }

    @Test
    void verify_downstreamFailure_isNotCached() {
        when(client.verify(KEY))
                .thenThrow(new UpstreamUnavailableException("odp-service", "down", null))
                .thenReturn(VALID);
        ApiKeyVerifier verifier = verifier();

        assertThatThrownBy(() -> verifier.verify(KEY)).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(verifier.verify(KEY)).isEqualTo(VALID);
        verify(client, times(2)).verify(KEY);
    }

    @Test
    void verify_malformedKey_neverCallsDownstream() {
        ApiKeyVerifier verifier = verifier();
        assertThat(verifier.verify("not-a-key").valid()).isFalse();
        assertThat(verifier.verify("opto_short").valid()).isFalse();
        assertThat(verifier.verify(null).valid()).isFalse();
        verify(client, never()).verify(anyString());
        assertThat(verifier.cachedEntries()).isZero();
    }

    @Test
    void verify_nullResponse_treatedAsInvalid() {
        when(client.verify(KEY)).thenReturn(null);
        assertThat(verifier().verify(KEY).valid()).isFalse();
    }
}
