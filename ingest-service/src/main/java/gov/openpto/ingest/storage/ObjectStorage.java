package gov.openpto.ingest.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * Minimal S3-shaped object store. {@link LocalFsObjectStorage} emulates buckets as folders for local runs;
 * {@link S3ObjectStorage} (profile {@code aws}) talks to Amazon S3.
 */
public interface ObjectStorage {

    /**
     * Stores an object (replacing any existing one).
     *
     * @param contentLength length if known, otherwise {@code -1}
     */
    ObjectMetadata put(String bucket, String key, InputStream data, long contentLength, String contentType)
            throws IOException;

    /** Opens the object for reading; the caller closes the stream. */
    InputStream get(String bucket, String key) throws IOException;

    /** Copies the object into {@code out} (for streamed downloads). */
    default long stream(String bucket, String key, OutputStream out) throws IOException {
        try (InputStream in = get(bucket, key)) {
            return in.transferTo(out);
        }
    }

    Optional<ObjectMetadata> head(String bucket, String key) throws IOException;

    /** @return {@code true} if an object was deleted */
    boolean delete(String bucket, String key) throws IOException;

    List<ObjectMetadata> list(String bucket, String prefix) throws IOException;
}