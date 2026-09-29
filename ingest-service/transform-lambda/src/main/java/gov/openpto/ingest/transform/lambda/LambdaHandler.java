package gov.openpto.ingest.transform.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.JsonArrayRecordWriter;
import gov.openpto.ingest.transform.TransformOptions;
import gov.openpto.ingest.transform.TransformSummary;
import gov.openpto.ingest.transform.Transformer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Lambda entry point: triggered by an S3 {@code ObjectCreated} notification on the raw bucket, it streams
 * the XML object through the {@link Transformer} and writes a JSON array to the processed bucket.
 *
 * <p>Handler string: {@code gov.openpto.ingest.transform.lambda.LambdaHandler::handleRequest}. Locally the
 * ingest-service calls the same method with an {@link S3Event} it builds itself and file-system backed
 * {@link ObjectReader}/{@link ObjectWriter}s.
 */
public class LambdaHandler implements RequestHandler<S3Event, TransformResult> {

    public static final String OUTPUT_BUCKET_ENV = "OUTPUT_BUCKET";
    public static final String DEFAULT_OUTPUT_BUCKET = "openpto-processed";

    private final ObjectReader reader;
    private final ObjectWriter writer;
    private final String outputBucket;
    private final Transformer transformer;

    /** Used by the AWS Lambda runtime. */
    public LambdaHandler() {
        this(S3ObjectIO.create());
    }

    private LambdaHandler(S3ObjectIO io) {
        this(io, io, outputBucketFromEnv(), new Transformer());
    }

    public LambdaHandler(ObjectReader reader, ObjectWriter writer, String outputBucket) {
        this(reader, writer, outputBucket, new Transformer());
    }

    public LambdaHandler(ObjectReader reader, ObjectWriter writer, String outputBucket, Transformer transformer) {
        this.reader = reader;
        this.writer = writer;
        this.outputBucket = outputBucket;
        this.transformer = transformer;
    }

    static String outputBucketFromEnv() {
        String env = System.getenv(OUTPUT_BUCKET_ENV);
        return env == null || env.isBlank() ? DEFAULT_OUTPUT_BUCKET : env;
    }

    @Override
    public TransformResult handleRequest(S3Event event, Context context) {
        List<ObjectResult> results = new ArrayList<>();
        if (event == null || event.getRecords() == null) {
            return new TransformResult(results);
        }
        for (S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getUrlDecodedKey();
            results.add(transformObject(bucket, key, context));
        }
        return new TransformResult(results);
    }

    private ObjectResult transformObject(String bucket, String key, Context context) {
        long start = System.nanoTime();
        String jobId = ObjectKeys.jobId(key);
        String outputKey = ObjectKeys.outputKey(key);
        log(context, "transform s3://" + bucket + "/" + key + " -> s3://" + outputBucket + "/" + outputKey);
        try (InputStream in = reader.open(bucket, key);
             OutputStream out = writer.create(outputBucket, outputKey, "application/json");
             JsonArrayRecordWriter sink = new JsonArrayRecordWriter(out)) {
            TransformSummary summary = transformer.transform(in, sink, TransformOptions.forJob(jobId));
            long ms = (System.nanoTime() - start) / 1_000_000;
            log(context, "done " + key + ": " + summary.recordsOk() + " ok, " + summary.recordsFailed() + " failed in "
                    + ms + " ms");
            return new ObjectResult(bucket, key, outputBucket, outputKey, jobId, summary.format(), summary.documents(),
                    summary.recordsTotal(), summary.recordsOk(), summary.recordsFailed(), summary.errors(), ms, null);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log(context, "failed " + key + ": " + e);
            return new ObjectResult(bucket, key, outputBucket, null, jobId, DocumentFormat.UNKNOWN, 0, 0, 0, 0,
                    List.of(), ms, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(message);
        }
    }
}