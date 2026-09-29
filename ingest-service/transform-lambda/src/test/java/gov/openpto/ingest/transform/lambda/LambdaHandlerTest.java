package gov.openpto.ingest.transform.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.Json;
import gov.openpto.ingest.transform.TestSupport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class LambdaHandlerTest {

    private static final String JOB = "3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f";

    /** In-memory "S3" so the handler runs exactly as in AWS, minus the network. */
    private final Map<String, byte[]> store = new HashMap<>();
    private final List<String> logs = new ArrayList<>();

    private final ObjectReader reader = (bucket, key) -> {
        byte[] data = store.get(bucket + "/" + key);
        if (data == null) {
            throw new FileNotFoundException("NoSuchKey: " + key);
        }
        return new ByteArrayInputStream(data);
    };

    private final ObjectWriter writer = (bucket, key, contentType) -> new ByteArrayOutputStream() {
        @Override
        public void close() {
            store.put(bucket + "/" + key, toByteArray());
        }
    };

    static S3Event event(String bucket, String key) {
        var object = new S3EventNotification.S3ObjectEntity(key, 10L, "etag", null, "0001");
        var bucketEntity = new S3EventNotification.S3BucketEntity(bucket, null, "arn:aws:s3:::" + bucket);
        var entity = new S3EventNotification.S3Entity("raw-created", bucketEntity, object, "1.0");
        var record = new S3EventNotification.S3EventNotificationRecord("us-east-1", "ObjectCreated:Put", "aws:s3",
                "2025-01-07T10:00:00.000Z", "2.1", null, null, entity, null);
        return new S3Event(List.of(record));
    }

    @Test
    void handleRequest_transformsRawObjectIntoJsonArrayInProcessedBucket() throws Exception {
        String key = ObjectKeys.rawKey(JOB, "ipg250107-sample.xml");
        store.put("openpto-raw/" + key, Files.readAllBytes(TestSupport.sample("ipg250107-sample.xml")));
        LambdaHandler handler = new LambdaHandler(reader, writer, "openpto-processed");

        TransformResult result = handler.handleRequest(event("openpto-raw", key),
                new LocalContext("openpto-transform", 60_000, logs::add));

        assertThat(result.objects()).singleElement().satisfies(o -> {
            assertThat(o.succeeded()).isTrue();
            assertThat(o.format()).isEqualTo(DocumentFormat.US_PATENT_GRANT);
            assertThat(o.recordsOk()).isEqualTo(5);
            assertThat(o.ingestJobId()).isEqualTo(JOB);
            assertThat(o.outputBucket()).isEqualTo("openpto-processed");
            assertThat(o.outputKey()).isEqualTo(key + ".json");
        });
        assertThat(result.recordsOk()).isEqualTo(5);
        assertThat(result.recordsFailed()).isZero();

        JsonNode json = Json.mapper().readTree(store.get("openpto-processed/" + key + ".json"));
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(5);
        JsonNode first = json.get(0);
        assertThat(first.get("patentNumber").asString()).isEqualTo("US12345601B2");
        assertThat(first.get("filingDate").asString()).isEqualTo("2021-09-23");
        assertThat(first.get("abstract").asString()).startsWith("A query cache");
        assertThat(first.get("ingestJobId").asString()).isEqualTo(JOB);
        assertThat(first.get("claims").get(1).get("dependsOn").asInt()).isEqualTo(1);
        assertThat(logs).anyMatch(l -> l.startsWith("done"));
    }

    @Test
    void handleRequest_missingObject_reportsFatalErrorPerObject() {
        LambdaHandler handler = new LambdaHandler(reader, writer, "openpto-processed");

        TransformResult result = handler.handleRequest(event("openpto-raw", "jobs/" + JOB + "/gone.xml"), LocalContext.silent());

        assertThat(result.objects()).singleElement().satisfies(o -> {
            assertThat(o.succeeded()).isFalse();
            assertThat(o.fatalError()).contains("NoSuchKey");
            assertThat(o.format()).isEqualTo(DocumentFormat.UNKNOWN);
        });
    }

    @Test
    void handleRequest_urlEncodedKeyIsDecoded_andNullEventIsEmpty() {
        store.put("openpto-raw/uploads/my file.xml", "<?xml version=\"1.0\"?>\n<x/>".getBytes(StandardCharsets.UTF_8));
        LambdaHandler handler = new LambdaHandler(reader, writer, "out");

        TransformResult result = handler.handleRequest(event("openpto-raw", "uploads/my+file.xml"), null);

        assertThat(result.objects()).singleElement().satisfies(o -> {
            assertThat(o.key()).isEqualTo("uploads/my file.xml");
            assertThat(o.ingestJobId()).isNull();
            assertThat(o.recordsFailed()).isEqualTo(1);
        });
        assertThat(new String(store.get("out/uploads/my file.xml.json"), StandardCharsets.UTF_8).trim()).isEqualTo("[\n\n]");
        assertThat(handler.handleRequest(null, null).objects()).isEmpty();
        assertThat(handler.handleRequest(new S3Event(), null).objects()).isEmpty();
    }

    @Test
    void localContext_exposesLambdaLikeMetadata() {
        List<String> lines = new ArrayList<>();
        LocalContext ctx = new LocalContext("fn", 1_000, lines::add);
        ctx.getLogger().log("a");
        ctx.getLogger().log("b".getBytes(StandardCharsets.UTF_8));

        assertThat(lines).containsExactly("a", "b");
        assertThat(ctx.getAwsRequestId()).isNotBlank();
        assertThat(ctx.getFunctionName()).isEqualTo("fn");
        assertThat(ctx.getInvokedFunctionArn()).endsWith(":function:fn");
        assertThat(ctx.getLogGroupName()).isEqualTo("/local/fn");
        assertThat(ctx.getLogStreamName()).isEqualTo("local");
        assertThat(ctx.getFunctionVersion()).isEqualTo("$LATEST");
        assertThat(ctx.getRemainingTimeInMillis()).isBetween(0, 1_000);
        assertThat(ctx.getMemoryLimitInMB()).isEqualTo(1024);
        assertThat(ctx.getIdentity()).isNull();
        assertThat(ctx.getClientContext()).isNull();
    }

    @Test
    void objectKeys_conventions() {
        assertThat(ObjectKeys.rawKey(JOB, "a.xml")).isEqualTo("jobs/" + JOB + "/a.xml");
        assertThat(ObjectKeys.jobId("jobs/" + JOB.toUpperCase() + "/a.xml")).isEqualTo(JOB);
        assertThat(ObjectKeys.jobId("other/a.xml")).isNull();
        assertThat(ObjectKeys.jobId(null)).isNull();
        assertThat(LambdaHandler.outputBucketFromEnv()).isNotBlank();
    }

    @Test
    void writerFailure_isFatalForThatObject() {
        String key = ObjectKeys.rawKey(JOB, "a.xml");
        store.put("openpto-raw/" + key, "<?xml version=\"1.0\"?>\n<us-patent-grant/>".getBytes(StandardCharsets.UTF_8));
        ObjectWriter failing = (bucket, k, type) -> new OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                throw new java.io.IOException("disk full");
            }
        };

        TransformResult result = new LambdaHandler(reader, failing, "p").handleRequest(event("openpto-raw", key), null);

        assertThat(result.objects().get(0).fatalError()).contains("disk full");
    }
}