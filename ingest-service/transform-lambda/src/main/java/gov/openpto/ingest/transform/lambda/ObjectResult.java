package gov.openpto.ingest.transform.lambda;

import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.RecordError;

import java.util.List;

/**
 * Result for one S3 object of the event.
 *
 * @param fatalError set when the object could not be processed at all (e.g. it could not be read);
 *                   the other counters are then 0
 */
public record ObjectResult(
        String bucket,
        String key,
        String outputBucket,
        String outputKey,
        String ingestJobId,
        DocumentFormat format,
        int documents,
        int recordsTotal,
        int recordsOk,
        int recordsFailed,
        List<RecordError> errors,
        long durationMs,
        String fatalError) {

    public boolean succeeded() {
        return fatalError == null;
    }
}