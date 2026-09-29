package gov.openpto.ingest.service;

import static gov.openpto.ingest.TestFixtures.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.ingest.TestFixtures;
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
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.RecordError;
import gov.openpto.ingest.transform.lambda.ObjectResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobStateServiceTest {

    @Mock
    IngestJobRepository jobs;
    @Mock
    IngestJobStageRepository stages;
    @Mock
    IngestJobErrorRepository errors;

    private JobStateService service;
    private IngestJob job;

    @BeforeEach
    void setUp() {
        service = new JobStateService(jobs, stages, errors, TestFixtures.properties(Path.of("x")));
        job = TestFixtures.job(ALICE, JobStatus.QUEUED);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobs.save(any(IngestJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ObjectResult result(int ok, int failed, List<RecordError> errs) {
        return new ObjectResult("openpto-raw", "jobs/x/a.xml", "openpto-processed", "jobs/x/a.xml.json", "x",
                DocumentFormat.US_PATENT_GRANT, ok + failed, ok + failed, ok, failed, errs, 12, null);
    }

    @Test
    void startParsing_queuedJob_movesToParsingAndCountsAttempt() {
        Optional<IngestJob> started = service.startParsing(job.getId());

        assertThat(started).isPresent();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PARSING);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test
    void startParsing_notQueuedOrMissing_isSkipped() {
        job.setStatus(JobStatus.COMPLETED);
        assertThat(service.startParsing(job.getId())).isEmpty();
        assertThat(service.startParsing(UUID.randomUUID())).isEmpty();
    }

    @Test
    void markTransformed_recordsCountsStagesAndErrors() {
        service.markTransformed(job.getId(), result(4, 1, List.of(new RecordError(3, "US1", "Malformed XML"))));

        assertThat(job.getStatus()).isEqualTo(JobStatus.TRANSFORMED);
        assertThat(job.getDocumentFormat()).isEqualTo(DocumentFormat.US_PATENT_GRANT);
        assertThat(job.getRecordsTotal()).isEqualTo(5);
        assertThat(job.getRecordsFailed()).isEqualTo(1);
        assertThat(job.getJsonObjectKey()).isEqualTo("jobs/x/a.xml.json");
        ArgumentCaptor<IngestJobStage> stage = ArgumentCaptor.forClass(IngestJobStage.class);
        verify(stages, org.mockito.Mockito.times(2)).save(stage.capture());
        assertThat(stage.getAllValues()).extracting(IngestJobStage::getStage)
                .containsExactly(StageName.PARSED, StageName.TRANSFORMED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngestJobError>> saved = ArgumentCaptor.forClass(List.class);
        verify(errors).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(e -> {
            assertThat(e.getRecordIndex()).isEqualTo(3);
            assertThat(e.getPhase()).isEqualTo(ErrorPhase.TRANSFORM);
        });
    }

    @Test
    void markTransformed_errorsCappedByMaxStoredErrors() {
        when(errors.countByJobId(job.getId())).thenReturn(1000L);

        service.markTransformed(job.getId(), result(0, 1, List.of(new RecordError(0, null, "x"))));

        verify(errors, never()).saveAll(anyList());
    }

    @ParameterizedTest
    @CsvSource({"5,5,0,COMPLETED", "5,4,1,PARTIAL", "5,0,5,FAILED", "0,0,0,FAILED", "5,3,0,PARTIAL"})
    void finalStatus_rules(int total, int loaded, int failed, JobStatus expected) {
        assertThat(JobStateService.finalStatus(total, loaded, failed)).isEqualTo(expected);
    }

    @Test
    void complete_allLoaded_isCompleted() {
        job.setRecordsTotal(5);
        IngestJob done = service.complete(job.getId(), new LoadOutcome(5, 0, List.of()));

        assertThat(done.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(done.getRecordsLoaded()).isEqualTo(5);
        assertThat(done.getFinishedAt()).isNotNull();
        assertThat(done.getMessage()).isEqualTo("Loaded 5 of 5 records");
    }

    @Test
    void complete_someRejected_isPartialWithLoadErrors() {
        job.setRecordsTotal(5);
        job.setRecordsFailed(1);
        IngestJob done = service.complete(job.getId(), new LoadOutcome(3, 1, List.of(new RecordError(2, "US3", "dup"))));

        assertThat(done.getStatus()).isEqualTo(JobStatus.PARTIAL);
        assertThat(done.getRecordsFailed()).isEqualTo(2);
        assertThat(done.getMessage()).contains("1 rejected");
        verify(errors).saveAll(anyList());
    }

    @Test
    void abortLoad_keepsPartialCounts() {
        job.setRecordsTotal(10);
        job.setRecordsFailed(2);

        IngestJob partial = service.abortLoad(job.getId(), new LoadOutcome(5, 0, List.of()), "odp down");
        assertThat(partial.getStatus()).isEqualTo(JobStatus.PARTIAL);
        assertThat(partial.getRecordsLoaded()).isEqualTo(5);
        assertThat(partial.getRecordsFailed()).isEqualTo(5);
        assertThat(partial.getMessage()).contains("after 5 of 8 records").contains("odp down");

        IngestJob failed = service.abortLoad(job.getId(), new LoadOutcome(0, 0, List.of()), "odp down");
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void fail_setsFailedWithStage() {
        IngestJob failed = service.fail(job.getId(), StageName.PARSED, "boom");

        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getMessage()).isEqualTo("boom");
        ArgumentCaptor<IngestJobStage> stage = ArgumentCaptor.forClass(IngestJobStage.class);
        verify(stages).save(stage.capture());
        assertThat(stage.getValue().getStatus()).isEqualTo(StageStatus.FAILED);
    }

    @Test
    void startLoading_andMissingJob() {
        service.startLoading(job.getId());
        assertThat(job.getStatus()).isEqualTo(JobStatus.LOADING);

        assertThatThrownBy(() -> service.startLoading(UUID.randomUUID()))
                .isInstanceOf(JobStateService.JobGoneException.class);
    }

    @Test
    void recoverStuck_failsInFlightJobs() {
        IngestJob parsing = TestFixtures.job(ALICE, JobStatus.PARSING);
        IngestJob loading = TestFixtures.job(ALICE, JobStatus.LOADING);
        when(jobs.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(parsing, loading));

        int recovered = service.recoverStuck(Duration.ofMinutes(10));

        assertThat(recovered).isEqualTo(2);
        assertThat(parsing.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(parsing.getMessage()).contains("Interrupted by restart");
        assertThat(loading.getStatus()).isEqualTo(JobStatus.FAILED);
    }
}