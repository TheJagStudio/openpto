package gov.openpto.ingest.transform.lambda;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Real S3 I/O used only inside AWS Lambda (the SDK is packaged by {@code lambdaZip}, not on the local classpath).
 * Writes spool to /tmp so objects of unknown length can be uploaded with a single PutObject.
 */
final class S3ObjectIO implements ObjectReader, ObjectWriter {

    private final S3Client s3;

    S3ObjectIO(S3Client s3) {
        this.s3 = s3;
    }

    static S3ObjectIO create() {
        return new S3ObjectIO(S3Client.create());
    }

    @Override
    public InputStream open(String bucket, String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public OutputStream create(String bucket, String key, String contentType) throws IOException {
        Path spool = Files.createTempFile("transform-", ".json");
        return new FilterOutputStream(new FileOutputStream(spool.toFile())) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                super.close();
                try {
                    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                            RequestBody.fromFile(spool));
                } finally {
                    Files.deleteIfExists(spool);
                }
            }
        };
    }
}