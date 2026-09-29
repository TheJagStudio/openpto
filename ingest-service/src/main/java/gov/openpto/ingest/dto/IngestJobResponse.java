package gov.openpto.ingest.dto;

import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.transform.DocumentFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "An ingest job: one uploaded XML file travelling through the S3 → Lambda → odp pipeline")
public record IngestJobResponse(
        @Schema(example = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f") UUID id,
        @Schema(example = "ipg250107.xml") String fileName,
        @Schema(example = "48213") long sizeBytes,
        @Schema(example = "COMPLETED") JobStatus status,
        @Schema(example = "US_PATENT_GRANT") DocumentFormat documentFormat,
        @Schema(example = "5") int recordsTotal,
        @Schema(example = "5") int recordsLoaded,
        @Schema(example = "0") int recordsFailed,
        List<RecordErrorResponse> errors,
        List<StageResponse> stages,
        @Schema(example = "jobs/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f/ipg250107.xml") String rawObjectKey,
        @Schema(example = "jobs/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f/ipg250107.xml.json") String jsonObjectKey,
        @Schema(example = "8d0c6a0e-2b7e-4c55-9a55-1f6f3f0f2a11") String ownerId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        @Schema(example = "734") Long durationMs,
        @Schema(description = "Last failure or summary message", example = "Loaded 5 of 5 records") String message) {

    @Schema(description = "A record that failed to transform or load")
    public record RecordErrorResponse(
            @Schema(example = "1") int index,
            @Schema(example = "US12345612B2") String identifier,
            @Schema(example = "Malformed XML in document #2 (starting line 44): ...") String message) {
    }

    @Schema(description = "Pipeline stage transition")
    public record StageResponse(
            @Schema(example = "TRANSFORMED") String stage,
            @Schema(example = "COMPLETED") String status,
            Instant at,
            @Schema(example = "5 records transformed, 0 failed") String message) {
    }
}