package gov.openpto.ingest.transform;

/**
 * @param ingestJobId copied into every record ({@code ingestJobId}); may be {@code null}
 * @param maxErrors   maximum number of {@link RecordError}s kept in the summary (all are counted)
 */
public record TransformOptions(String ingestJobId, int maxErrors) {

    public static final int DEFAULT_MAX_ERRORS = 1000;

    public static TransformOptions forJob(String ingestJobId) {
        return new TransformOptions(ingestJobId, DEFAULT_MAX_ERRORS);
    }
}