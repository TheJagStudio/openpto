package gov.openpto.gateway.identity;

/** A dependency the gateway itself needs (key verification, JWKS) could not be reached. */
public class UpstreamUnavailableException extends RuntimeException {

    private final String serviceName;

    public UpstreamUnavailableException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
