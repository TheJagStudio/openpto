package gov.openpto.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.dto.SampleResponse;
import gov.openpto.ingest.exception.NotFoundException;
import gov.openpto.ingest.storage.LocalFsObjectStorage;
import gov.openpto.ingest.storage.ObjectCreatedEvent;
import gov.openpto.ingest.storage.ObjectMetadata;
import gov.openpto.ingest.storage.StorageObjectIO;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.lambda.LambdaHandler;
import gov.openpto.ingest.transform.lambda.LocalContext;
import gov.openpto.ingest.transform.lambda.ObjectResult;
import gov.openpto.ingest.transform.lambda.TransformResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SupportServicesTest {

    @TempDir
    Path root;

    @Test
    void sampleService_listsCatalogWithSizes_andServesOnlyCatalogued() throws IOException {
        SampleService samples = new SampleService();

        List<SampleResponse> list = samples.list();

        assertThat(list).hasSize(5);
        assertThat(list).extracting(SampleResponse::format).contains(DocumentFormat.US_PATENT_GRANT,
                DocumentFormat.US_PATENT_APPLICATION, DocumentFormat.PATDOC_LEGACY, DocumentFormat.TRADEMARK_DAILY);
        assertThat(list).allSatisfy(s -> assertThat(s.sizeBytes()).isPositive());
        try (InputStream in = samples.get("ipg250107-sample.xml").getInputStream()) {
            assertThat(new String(in.readNBytes(5))).isEqualTo("<?xml");
        }
        assertThatThrownBy(() -> samples.get("../application.yml")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> samples.get("nope.xml")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void startupRecovery_delegatesWithConfiguredThreshold() {
        JobStateService state = mock(JobStateService.class);
        when(state.recoverStuck(Duration.ofMinutes(10))).thenReturn(2).thenReturn(0);
        StartupRecovery recovery = new StartupRecovery(state, TestFixtures.properties(root));

        recovery.recover();
        recovery.recover();

        verify(state, org.mockito.Mockito.times(2)).recoverStuck(Duration.ofMinutes(10));
    }

    @Test
    void transformInvoker_runsRealLambdaHandlerAgainstLocalBuckets() throws IOException {
        LocalFsObjectStorage storage = new LocalFsObjectStorage(root, null, Set.of());
        String jobId = UUID.randomUUID().toString();
        ObjectMetadata raw;
        try (InputStream in = Files.newInputStream(Path.of("samples", "ipg250107-sample.xml"))) {
            raw = storage.put("openpto-raw", "jobs/" + jobId + "/grant.xml", in, -1, "application/xml");
        }
        StorageObjectIO io = new StorageObjectIO(storage);
        LambdaHandler handler = new LambdaHandler(io, io, "openpto-processed");
        TransformInvoker invoker = new TransformInvoker(e -> handler.handleRequest(e, LocalContext.silent()));

        ObjectResult result = invoker.invoke(ObjectCreatedEvent.of(raw));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.recordsOk()).isEqualTo(5);
        assertThat(result.ingestJobId()).isEqualTo(jobId);
        assertThat(storage.head("openpto-processed", result.outputKey())).isPresent();
    }

    @Test
    void transformInvoker_emptyResultIsAnError() {
        TransformInvoker invoker = new TransformInvoker(e -> new TransformResult(List.of()));

        assertThatThrownBy(() -> invoker.invoke(new ObjectCreatedEvent("b", "k", 1, "e")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void s3Events_encodeKeyLikeS3() {
        S3Event event = S3Events.objectCreated(new ObjectCreatedEvent("openpto-raw", "jobs/a b+c.xml", 7, "etag"));

        var record = event.getRecords().get(0);
        assertThat(record.getEventName()).isEqualTo("ObjectCreated:Put");
        assertThat(record.getS3().getBucket().getName()).isEqualTo("openpto-raw");
        assertThat(record.getS3().getObject().getKey()).isEqualTo("jobs/a+b%2Bc.xml");
        assertThat(record.getS3().getObject().getUrlDecodedKey()).isEqualTo("jobs/a b+c.xml");
        assertThat(record.getS3().getObject().getSizeAsLong()).isEqualTo(7);
    }
}