package gov.openpto.gateway.identity;

/**
 * Response of odp-service {@code POST /internal/v1/api-keys/verify}:
 * {@code { valid, keyId, userId, tier, perMinute, perDay }}.
 */
public record ApiKeyVerification(boolean valid, String keyId, String userId, String tier,
                                 Long perMinute, Long perDay) {

    public static ApiKeyVerification invalid() {
        return new ApiKeyVerification(false, null, null, null, null, null);
    }
}
