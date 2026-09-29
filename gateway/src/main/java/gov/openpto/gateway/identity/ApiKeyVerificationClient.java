package gov.openpto.gateway.identity;

/** Port to odp-service's internal key verification endpoint (separated so the cache can be unit-tested). */
public interface ApiKeyVerificationClient {

    /**
     * @return the verification result; {@code valid=false} for unknown/revoked keys
     * @throws UpstreamUnavailableException when odp-service cannot be reached
     */
    ApiKeyVerification verify(String apiKey);
}
