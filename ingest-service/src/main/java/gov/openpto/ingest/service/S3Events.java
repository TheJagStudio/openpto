package gov.openpto.ingest.service;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import gov.openpto.ingest.storage.ObjectCreatedEvent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/** Builds the exact event shape S3 delivers to Lambda for {@code s3:ObjectCreated:Put}. */
public final class S3Events {

    private S3Events() {
    }

    public static S3Event objectCreated(ObjectCreatedEvent event) {
        // S3 URL-encodes keys in notifications (spaces as '+'); the handler decodes them again.
        String encodedKey = URLEncoder.encode(event.key(), StandardCharsets.UTF_8).replace("%2F", "/");
        var object = new S3EventNotification.S3ObjectEntity(encodedKey, event.size(), event.eTag(), null,
                Long.toHexString(System.nanoTime()));
        var bucket = new S3EventNotification.S3BucketEntity(event.bucket(),
                new S3EventNotification.UserIdentityEntity("openpto-local"), "arn:aws:s3:::" + event.bucket());
        var entity = new S3EventNotification.S3Entity("openpto-raw-object-created", bucket, object, "1.0");
        var record = new S3EventNotification.S3EventNotificationRecord("local", "ObjectCreated:Put", "aws:s3",
                Instant.now().toString(), "2.1", null, null, entity, null);
        return new S3Event(List.of(record));
    }
}