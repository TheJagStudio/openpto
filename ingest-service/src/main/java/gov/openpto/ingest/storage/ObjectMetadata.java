package gov.openpto.ingest.storage;

import java.time.Instant;

/** S3-like object metadata ({@code eTag} is the hex MD5 of the content, as for a single-part S3 upload). */
public record ObjectMetadata(String bucket, String key, long size, String eTag, Instant lastModified) {
}