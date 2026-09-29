package gov.openpto.ingest.transform.lambda;

import java.util.List;

/** Lambda response: one entry per S3 record in the triggering event. */
public record TransformResult(List<ObjectResult> objects) {

    public int recordsOk() {
        return objects.stream().mapToInt(ObjectResult::recordsOk).sum();
    }

    public int recordsFailed() {
        return objects.stream().mapToInt(ObjectResult::recordsFailed).sum();
    }
}