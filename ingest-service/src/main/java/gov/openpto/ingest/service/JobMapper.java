package gov.openpto.ingest.service;

import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobError;
import gov.openpto.ingest.model.IngestJobStage;

import java.time.Duration;
import java.util.List;

/** Entity → contract DTO. */
public final class JobMapper {

    private JobMapper() {
    }

    public static IngestJobResponse toResponse(IngestJob job, List<IngestJobStage> stages, List<IngestJobError> errors) {
        Long duration = job.getStartedAt() != null && job.getFinishedAt() != null
                ? Duration.between(job.getStartedAt(), job.getFinishedAt()).toMillis()
                : null;
        return new IngestJobResponse(
                job.getId(),
                job.getFileName(),
                job.getSizeBytes(),
                job.getStatus(),
                job.getDocumentFormat(),
                job.getRecordsTotal(),
                job.getRecordsLoaded(),
                job.getRecordsFailed(),
                errors.stream()
                        .map(e -> new IngestJobResponse.RecordErrorResponse(e.getRecordIndex(), e.getIdentifier(),
                                e.getMessage()))
                        .toList(),
                stages.stream()
                        .map(s -> new IngestJobResponse.StageResponse(s.getStage().name(), s.getStatus().name(),
                                s.getAt(), s.getMessage()))
                        .toList(),
                job.getRawObjectKey(),
                job.getJsonObjectKey(),
                job.getOwnerId(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                duration,
                job.getMessage());
    }
}