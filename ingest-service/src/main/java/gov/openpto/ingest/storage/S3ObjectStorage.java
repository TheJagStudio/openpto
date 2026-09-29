package gov.openpto.ingest.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Amazon S3 implementation (profile {@code aws}). In AWS the raw bucket's own event notification triggers the
 * transform Lambda, so this class never publishes {@link ObjectCreatedEvent}s itself.
 * The SDK is compile-only locally; build with {@code -Paws} to ship it.
 */
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;

    public S3ObjectStorage(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public ObjectMetadata put(String bucket, String key, InputStream data, long contentLength, String contentType)
            throws IOException {
        ObjectKeyValidator.validateKey(key);
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        PutObjectResponse response;
        long size;
        if (contentLength >= 0) {
            response = s3.putObject(request, RequestBody.fromInputStream(data, contentLength));
            size = contentLength;
        } else {
            Path spool = Files.createTempFile("s3-put-", ".tmp");
            try {
                Files.copy(data, spool, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                size = Files.size(spool);
                response = s3.putObject(request, RequestBody.fromFile(spool));
            } finally {
                Files.deleteIfExists(spool);
            }
        }
        return new ObjectMetadata(bucket, key, size, stripQuotes(response.eTag()), java.time.Instant.now());
    }

    @Override
    public InputStream get(String bucket, String key) throws IOException {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new NoSuchObjectException(bucket, key);
        }
    }

    @Override
    public Optional<ObjectMetadata> head(String bucket, String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new ObjectMetadata(bucket, key, head.contentLength(), stripQuotes(head.eTag()),
                    head.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String bucket, String key) {
        boolean existed = head(bucket, key).isPresent();
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        return existed;
    }

    @Override
    public List<ObjectMetadata> list(String bucket, String prefix) {
        return s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().stream()
                .map(o -> new ObjectMetadata(bucket, o.key(), o.size(), stripQuotes(o.eTag()), o.lastModified()))
                .toList();
    }

    private static String stripQuotes(String eTag) {
        return eTag == null ? null : eTag.replace("\"", "");
    }
}