package gov.openpto.gateway.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import gov.openpto.gateway.config.GatewayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Verifies API keys against odp-service with a Caffeine cache: valid results live 60s, invalid ones 10s
 * (both configurable). Failures to reach odp-service are never cached. Keys are cached by SHA-256 so no
 * plaintext secret sits in the heap longer than the request.
 */
@Component
public class ApiKeyVerifier {

    /** Cheap shape check so obvious junk never reaches odp-service ({@code opto_} + base62). */
    private static final Pattern KEY_SHAPE = Pattern.compile("^opto_[A-Za-z0-9]{20,128}$");

    private final ApiKeyVerificationClient client;
    private final Cache<String, ApiKeyVerification> cache;

    @Autowired
    public ApiKeyVerifier(ApiKeyVerificationClient client, GatewayProperties properties) {
        this(client, properties.apiKeys(), Ticker.systemTicker());
    }

    ApiKeyVerifier(ApiKeyVerificationClient client, GatewayProperties.ApiKeys config, Ticker ticker) {
        this.client = client;
        long positiveNanos = config.positiveTtl().toNanos();
        long negativeNanos = config.negativeTtl().toNanos();
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.maxEntries())
                .ticker(ticker)
                .expireAfter(new Expiry<String, ApiKeyVerification>() {
                    @Override
                    public long expireAfterCreate(String key, ApiKeyVerification value, long currentTime) {
                        return value.valid() ? positiveNanos : negativeNanos;
                    }

                    @Override
                    public long expireAfterUpdate(String key, ApiKeyVerification value, long currentTime,
                                                  long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, ApiKeyVerification value, long currentTime,
                                                long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * @return verification result (never null)
     * @throws UpstreamUnavailableException when odp-service is unreachable and nothing is cached
     */
    public ApiKeyVerification verify(String apiKey) {
        if (apiKey == null || !KEY_SHAPE.matcher(apiKey).matches()) {
            return ApiKeyVerification.invalid();
        }
        return cache.get(sha256(apiKey), ignored -> {
            ApiKeyVerification result = client.verify(apiKey);
            return result == null ? ApiKeyVerification.invalid() : result;
        });
    }

    long cachedEntries() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
