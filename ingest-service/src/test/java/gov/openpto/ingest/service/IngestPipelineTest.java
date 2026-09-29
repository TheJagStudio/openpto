package gov.openpto.ingest.service;

import static gov.openpto.ingest.TestFixtures.ALICE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.model.StageName;
import gov.openpto.ingest.storage.ObjectCreatedEvent;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.lambda.ObjectResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestPipelineTest {

    @Mock
    JobStateService jobState;
    @Mock
    TransformInvoker invoker;
    @Mock
    RecordLoader loader;

    private IngestPipeline pipeline;
    private IngestJob job;
    private ObjectCreatedEvent event;

    @BeforeEach
    void setUp() {
        pipeline = new IngestPipeline(jobState, invoker, loader, TestFixtures.properties(Path.of("x")));
        job = TestFixtures.job(ALICE, JobStatus.PARSING);
        job.setDocumentFormat(DocumentFormat.US_PATENT_GRANT);
        event = new ObjectCreatedEvent("openpto-raw", job.getRawObjectKey(), 100, "etag");
        when(jobState.startParsing(job.getId())).thenReturn(Optional.of(job));
        when(jobState.markTransformed(eq(job.getId()), any())).thenReturn(job);
    }

    private ObjectResult result(int ok, int failed) {
        return new ObjectResult("openpto-raw", event.key(), "openpto-processed", event.key() + ".json",
                job.getId().toString(), DocumentFormat.US_PATENT_GRANT, 1, ok + failed, ok, failed, List.of(), 5, null);
    }

    @Test
    void process_happyPath_transformsLoadsAndCompletes() throws IOException {
        when(invoker.invoke(event)).thenReturn(result(5, 0));
        LoadOutcome outcome = new LoadOutcome(5, 0, List.of());
        when(loader.load(DocumentFormat.US_PATENT_GRANT, "openpto-processed", event.key() + ".json")).thenReturn(outcome);
        when(jobState.complete(job.getId(), outcome)).thenReturn(job);

        pipeline.onObjectCreated(event);

        verify(jobState).markTransformed(eq(job.getId()), any());
        verify(jobState).startLoading(job.getId());
        verify(jobState).complete(job.getId(), outcome);
    }

    @Test
    void process_transformFatalError_failsAtParsedStage() {
        when(invoker.invoke(event)).thenReturn(new ObjectResult("openpto-raw", event.key(), "p", null, null,
                DocumentFormat.UNKNOWN, 0, 0, 0, 0, List.of(), 1, "NoSuchKey"));

        pipeline.process(event);

        verify(jobState).fail(eq(job.getId()), eq(StageName.PARSED), contains("NoSuchKey"));
        verifyNoInteractions(loader);
    }

    @Test
    void process_transformThrows_failsAtParsedStage() {
        when(invoker.invoke(event)).thenThrow(new IllegalStateException("lambda crashed"));

        pipeline.process(event);

        verify(jobState).fail(eq(job.getId()), eq(StageName.PARSED), contains("lambda crashed"));
    }

    @Test
    void process_noValidRecords_failsWithoutLoading() {
        when(invoker.invoke(event)).thenReturn(result(0, 2));

        pipeline.process(event);

        verify(jobState).fail(job.getId(), StageName.LOADED, "No valid records to load");
        verifyNoInteractions(loader);
    }

    @Test
    void process_emptyFile_failsWithNoRecordsFound() {
        when(invoker.invoke(event)).thenReturn(result(0, 0));

        pipeline.process(event);

        verify(jobState).fail(job.getId(), StageName.LOADED, "No records found in the file");
    }

    @Test
    void process_loadAborted_recordsPartialProgress() throws IOException {
        when(invoker.invoke(event)).thenReturn(result(5, 0));
        LoadOutcome partial = new LoadOutcome(2, 0, List.of());
        when(loader.load(any(), anyString(), anyString())).thenThrow(new LoadAbortedException("odp down", partial, null));

        pipeline.process(event);

        verify(jobState).abortLoad(job.getId(), partial, "odp down");
    }

    @Test
    void process_loadIoError_failsAtLoadedStage() throws IOException {
        when(invoker.invoke(event)).thenReturn(result(5, 0));
        when(loader.load(any(), anyString(), anyString())).thenThrow(new IOException("disk"));

        pipeline.process(event);

        verify(jobState).fail(eq(job.getId()), eq(StageName.LOADED), contains("disk"));
    }

    @Test
    void process_ignoresOtherBucketsForeignKeysAndStaleEvents() {
        pipeline.process(new ObjectCreatedEvent("openpto-processed", event.key(), 1, "e"));
        pipeline.process(new ObjectCreatedEvent("openpto-raw", "random/key.xml", 1, "e"));
        UUID other = UUID.randomUUID();
        when(jobState.startParsing(other)).thenReturn(Optional.empty());
        pipeline.process(new ObjectCreatedEvent("openpto-raw", "jobs/" + other + "/a.xml", 1, "e"));

        verifyNoInteractions(invoker);
    }

    @Test
    void process_jobDeletedMidway_stopsQuietly() {
        when(invoker.invoke(event)).thenReturn(result(5, 0));
        when(jobState.markTransformed(eq(job.getId()), any())).thenThrow(new JobStateService.JobGoneException(job.getId()));

        pipeline.process(event);

        verify(jobState, never()).fail(any(), any(), anyString());
    }

    @Test
    void process_unexpectedError_marksFailedBestEffort() {
        when(invoker.invoke(event)).thenReturn(result(5, 0));
        when(jobState.markTransformed(eq(job.getId()), any())).thenThrow(new IllegalStateException("db down"));
        when(jobState.fail(any(), any(), anyString())).thenThrow(new IllegalStateException("still down"));

        pipeline.process(event);

        verify(jobState).fail(eq(job.getId()), eq(StageName.LOADED), contains("db down"));
    }
}