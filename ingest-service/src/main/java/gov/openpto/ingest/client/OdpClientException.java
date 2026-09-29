package gov.openpto.ingest.client;

/** odp-service could not accept a batch (4xx, or 5xx/connection errors after all retries). */
public class OdpClientException extends RuntimeException {

    private final boolean retriesExhausted;

    public OdpClientException(String message, Throwable cause, boolean retriesExhausted) {
        super(message, cause);
        this.retriesExhausted = retriesExhausted;
    }

    public boolean retriesExhausted() {
        return retriesExhausted;
    }
}