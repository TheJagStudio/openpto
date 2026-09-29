package gov.openpto.ingest.service;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.model.ErrorPhase;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobError;
import gov.openpto.ingest.model.IngestJobStage;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.model.StageName;
import gov.openpto.ingest.model.StageStatus;
import gov.openpto.ingest.repository.IngestJobErrorRepository;
import gov.openpto.ingest.repository.IngestJobRepository;
import gov.openpto.ingest.repository.IngestJobStageRepository;
import gov.openpto.ingest.transform.RecordError;
import gov.openpto.ingest.transform.lambda.ObjectResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The job state machine. Every transition is its own short transaction (the pipeline runs outside any
 * transaction), protected by optimistic locking ({@code @Version}).
 */
@Slf4j
@Service
@Transactional
public class JobStateService {

    private final IngestJobRepository jobs;
    private final IngestJobStageRepository stages;
    private final IngestJobErrorRepository errors;
    private final int maxStoredErrors;

    public JobStateService(IngestJobRepository jobs, IngestJobStageRepository stages, IngestJobErrorRepository errors,
                           AppProperties properties) {
        this.jobs = jobs;
        this.stages = stages;
        this.errors = errors;
        this.maxStoredErrors = properties.ingest().maxStoredErrors();
    }

    /** QUEUED → PARSING; empty if the job is gone or not queued (duplicate / stale event). */
    public Optional<IngestJob> startParsing(UUID jobId) {
        Optional<IngestJob> found = jobs.findById(jobId);
        if (found.isEmpty() || found.get().getStatus() != JobStatus.QUEUED) {
            return Optional.empty();
        }
        IngestJob job = found.get();
        job.setStatus(JobStatus.PARSING);
        job.setStartedAt(Instant.now());
        job.setFinishedAt(null);
        job.setAttempts(job.getAttempts() + 1);
        job.setMessage(null);
        return Optional.of(jobs.save(job));
    }

    public IngestJob markTransformed(UUID jobId, ObjectResult result) {
        IngestJob job = require(jobId);
        job.setDocumentFormat(result.format());
        job.setRecordsTotal(result.recordsTotal());
        job.setRecordsFailed(result.recordsFailed());
        job.setRecordsLoaded(0);
        job.setJsonBucket(result.outputBucket());
        job.setJsonObjectKey(result.outputKey());
        job.setStatus(JobStatus.TRANSFORMED);
        String summary = result.recordsOk() + " records transformed, " + result.recordsFailed() + " failed";
        job.setMessage(summary);
        stages.save(new IngestJobStage(jobId, StageName.PARSED,
                result.documents() > 0 ? StageStatus.COMPLETED : StageStatus.FAILED,
                result.documents() + " XML document(s) parsed, format " + result.format()));
        stages.save(new IngestJobStage(jobId, StageName.TRANSFORMED,
                result.recordsOk() > 0 ? StageStatus.COMPLETED : StageStatus.FAILED, summary + " in "
                + result.durationMs() + " ms"));
        saveErrors(jobId, result.errors(), ErrorPhase.TRANSFORM);
        return jobs.save(job);
    }

    public void startLoading(UUID jobId) {
        IngestJob job = require(jobId);
        job.setStatus(JobStatus.LOADING);
        jobs.save(job);
    }

    public IngestJob complete(UUID jobId, LoadOutcome outcome) {
        IngestJob job = require(jobId);
        job.setRecordsLoaded(outcome.loaded());
        job.setRecordsFailed(job.getRecordsFailed() + outcome.failed());
        JobStatus status = finalStatus(job.getRecordsTotal(), job.getRecordsLoaded(), job.getRecordsFailed());
        String message = "Loaded " + outcome.loaded() + " of " + job.getRecordsTotal() + " records"
                + (outcome.failed() > 0 ? " (" + outcome.failed() + " rejected by odp-service)" : "");
        stages.save(new IngestJobStage(jobId, StageName.LOADED,
                outcome.loaded() > 0 ? StageStatus.COMPLETED : StageStatus.FAILED, message));
        saveErrors(jobId, outcome.errors(), ErrorPhase.LOAD);
        return finish(job, status, message);
    }

    /** odp-service went away mid-load: keep what was loaded, count the rest as failed. */
    public IngestJob abortLoad(UUID jobId, LoadOutcome partial, String reason) {
        IngestJob job = require(jobId);
        int transformedOk = job.getRecordsTotal() - job.getRecordsFailed();
        job.setRecordsLoaded(partial.loaded());
        job.setRecordsFailed(job.getRecordsTotal() - partial.loaded());
        String message = "Load aborted after " + partial.loaded() + " of " + transformedOk + " records: " + reason;
        stages.save(new IngestJobStage(jobId, StageName.LOADED, StageStatus.FAILED, message));
        saveErrors(jobId, partial.errors(), ErrorPhase.LOAD);
        return finish(job, partial.loaded() > 0 ? JobStatus.PARTIAL : JobStatus.FAILED, message);
    }

    public IngestJob fail(UUID jobId, StageName stage, String reason) {
        IngestJob job = require(jobId);
        stages.save(new IngestJobStage(jobId, stage, StageStatus.FAILED, reason));
        return finish(job, JobStatus.FAILED, reason);
    }

    /** On startup: jobs left in flight by a crash/restart are failed so they can be retried. */
    public int recoverStuck(Duration olderThan) {
        List<IngestJob> stuck = jobs.findByStatusInAndUpdatedAtBefore(JobStatus.IN_FLIGHT, Instant.now().minus(olderThan));
        for (IngestJob job : stuck) {
            StageName stage = switch (job.getStatus()) {
                case QUEUED, PARSING -> StageName.PARSED;
                case TRANSFORMED, LOADING -> StageName.LOADED;
                default -> StageName.UPLOADED;
            };
            String reason = "Interrupted by restart while " + job.getStatus() + "; retry the job";
            stages.save(new IngestJobStage(job.getId(), stage, StageStatus.FAILED, reason));
            finish(job, JobStatus.FAILED, reason);
            log.warn("Job {} was {} for more than {}; marked FAILED", job.getId(), job.getStatus(), olderThan);
        }
        return stuck.size();
    }

    static JobStatus finalStatus(int total, int loaded, int failed) {
        if (total == 0 || loaded == 0) {
            return JobStatus.FAILED;
        }
        return failed == 0 && loaded >= total ? JobStatus.COMPLETED : JobStatus.PARTIAL;
    }

    private IngestJob finish(IngestJob job, JobStatus status, String message) {
        job.setStatus(status);
        job.setMessage(message);
        job.setFinishedAt(Instant.now());
        return jobs.save(job);
    }

    private void saveErrors(UUID jobId, List<RecordError> recordErrors, ErrorPhase phase) {
        if (recordErrors == null || recordErrors.isEmpty()) {
            return;
        }
        long room = maxStoredErrors - errors.countByJobId(jobId);
        if (room <= 0) {
            return;
        }
        errors.saveAll(recordErrors.stream()
                .limit(room)
                .map(e -> new IngestJobError(jobId, e.index(), e.identifier(), e.message(), phase))
                .toList());
    }

    private IngestJob require(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new JobGoneException(jobId));
    }

    /** The job was deleted while the pipeline was working on it. */
    public static class JobGoneException extends RuntimeException {
        public JobGoneException(UUID jobId) {
            super("Job " + jobId + " no longer exists");
        }
    }
}