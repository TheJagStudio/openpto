package gov.openpto.ingest.storage;

import gov.openpto.ingest.transform.lambda.ObjectReader;
import gov.openpto.ingest.transform.lambda.ObjectWriter;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adapts {@link ObjectStorage} to the transform-lambda's {@link ObjectReader}/{@link ObjectWriter}, so the
 * function code runs unchanged locally. Output is spooled to a temp file and put on close (like S3 PutObject).
 */
public class StorageObjectIO implements ObjectReader, ObjectWriter {

    private final ObjectStorage storage;

    public StorageObjectIO(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public InputStream open(String bucket, String key) throws IOException {
        return storage.get(bucket, key);
    }

    @Override
    public OutputStream create(String bucket, String key, String contentType) throws IOException {
        Path spool = Files.createTempFile("ingest-out-", ".json");
        return new FilterOutputStream(Files.newOutputStream(spool)) {
            private boolean closed;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    super.close();
                    try (InputStream in = Files.newInputStream(spool)) {
                        storage.put(bucket, key, in, Files.size(spool), contentType);
                    }
                } finally {
                    Files.deleteIfExists(spool);
                }
            }
        };
    }
}