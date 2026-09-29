package gov.openpto.ingest.service;

/** Loading stopped part-way (odp-service unavailable after retries); carries what was loaded so far. */
public class LoadAbortedException extends RuntimeException {

    private final transient LoadOutcome partial;

    public LoadAbortedException(String message, LoadOutcome partial, Throwable cause) {
        super(message, cause);
        this.partial = partial;
    }

    public LoadOutcome partial() {
        return partial;
    }
}