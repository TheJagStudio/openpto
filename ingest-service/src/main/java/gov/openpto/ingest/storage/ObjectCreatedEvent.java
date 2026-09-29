package gov.openpto.ingest.storage;

/**
 * Local equivalent of an S3 {@code s3:ObjectCreated:*} notification: {@code { bucket, key, size, eTag }}.
 * Published after a put into a bucket that has notifications enabled (the raw bucket).
 */
public record ObjectCreatedEvent(String bucket, String key, long size, String eTag) {

    public static ObjectCreatedEvent of(ObjectMetadata metadata) {
        return new ObjectCreatedEvent(metadata.bucket(), metadata.key(), metadata.size(), metadata.eTag());
    }
}