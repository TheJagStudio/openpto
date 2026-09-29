package gov.openpto.ingest.transform.lambda;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Creates an object by streaming into it; the object becomes visible when the returned stream is closed.
 * In AWS this is S3 PutObject; locally the file-system bucket emulation.
 */
@FunctionalInterface
public interface ObjectWriter {
    OutputStream create(String bucket, String key, String contentType) throws IOException;
}