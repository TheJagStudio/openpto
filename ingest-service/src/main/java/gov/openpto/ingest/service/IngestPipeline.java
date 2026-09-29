package gov.openpto.ingest.service;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.config.AsyncConfig;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.StageName;
import gov.openpto.ingest.storage.ObjectCreatedEvent;
import gov.openpto.ingest.transform.lambda.ObjectKeys;
import gov.openpto.ingest.transform.lambda.ObjectResult;

import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Local stand-in for "S3 event notification → Lambda → database load": reacts to {@link ObjectCreatedEvent}s of
 * the raw bucket after the upload transaction committed, runs the transform function on the bounded ingest
 * executor, then bulk-loads the JSON into odp-service and records the outcome.
 */
@Slf4j
@Component
public class IngestPipeline {

    private final JobStateService jobState;
    private final TransformInvoker transformInvoker;
    private final RecordLoader loader;
    private final String rawBucket;

    public IngestPipeline(JobStateService jobState, TransformInvoker transformInvoker, RecordLoader loader,
                          AppProperties properties) {
        this.jobState = jobState;
        this.transformInvoker = transformInvoker;
        this.loader = loader;
        this.rawBucket = properties.storage().rawBucket();
    }

    @Async(AsyncConfig.INGEST_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onObjectCreated(ObjectCreatedEvent event) {
        process(event);
    }

    public void process(ObjectCreatedEvent event) {
        if (!rawBucket.equals(event.bucket())) {
            return;
        }
        String jobIdText = ObjectKeys.jobId(event.key());
        if (jobIdText == null) {
            log.warn("Ignoring object s3://{}/{}: key does not belong to an ingest job", event.bucket(), event.key());
            return;
        }
        UUID jobId = UUID.fromString(jobIdText);
        MDC.put("jobId", jobIdText);
        try {
            run(jobId, event);
        } catch (JobStateService.JobGoneException e) {
            log.info("Job {} was deleted while processing; stopping", jobId);
        } catch (RuntimeException e) {
            log.error("Ingest job {} failed unexpectedly", jobId, e);
            safeFail(jobId, StageName.LOADED, "Unexpected error: " + e.getMessage());
        } finally {
            MDC.remove("jobId");
        }
    }

    private void run(UUID jobId, ObjectCreatedEvent event) {
        Optional<IngestJob> started = jobState.startParsing(jobId);
        if (started.isEmpty()) {
            log.info("Job {} is not QUEUED (duplicate or stale event); skipping", jobId);
            return;
        }
        log.info("Job {}: transforming s3://{}/{} ({} bytes)", jobId, event.bucket(), event.key(), event.size());

        ObjectResult result;
        try {
            result = transformInvoker.invoke(event);
        } catch (RuntimeException e) {
            jobState.fail(jobId, StageName.PARSED, "Transform function failed: " + e.getMessage());
            return;
        }
        if (!result.succeeded()) {
            jobState.fail(jobId, StageName.PARSED, "Transform function failed: " + result.fatalError());
            return;
        }
        IngestJob transformed = jobState.markTransformed(jobId, result);
        if (result.recordsOk() == 0) {
            jobState.fail(jobId, StageName.LOADED, result.recordsTotal() == 0
                    ? "No records found in the file" : "No valid records to load");
            return;
        }

        jobState.startLoading(jobId);
        try {
            LoadOutcome outcome = loader.load(transformed.getDocumentFormat(), result.outputBucket(), result.outputKey());
            IngestJob done = jobState.complete(jobId, outcome);
            log.info("Job {} finished {}: {} loaded, {} failed", jobId, done.getStatus(), done.getRecordsLoaded(),
                    done.getRecordsFailed());
        } catch (LoadAbortedException e) {
            jobState.abortLoad(jobId, e.partial(), e.getMessage());
        } catch (Exception e) {
            jobState.fail(jobId, StageName.LOADED, "Load failed: " + e.getMessage());
        }
    }

    private void safeFail(UUID jobId, StageName stage, String message) {
        try {
            jobState.fail(jobId, stage, message);
        } catch (RuntimeException e) {
            log.warn("Could not mark job {} as failed: {}", jobId, e.getMessage());
        }
    }
}