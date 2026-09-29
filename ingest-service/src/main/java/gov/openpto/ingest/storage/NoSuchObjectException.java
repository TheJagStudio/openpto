package gov.openpto.ingest.storage;

import java.io.FileNotFoundException;

/** Equivalent of S3 {@code NoSuchKey}. */
public class NoSuchObjectException extends FileNotFoundException {
    public NoSuchObjectException(String bucket, String key) {
        super("NoSuchKey: s3://" + bucket + "/" + key);
    }
}