package gov.openpto.ingest.transform.lambda;

import java.io.IOException;
import java.io.InputStream;

/** Opens an object for streaming read. In AWS this is S3 GetObject; locally the file-system bucket emulation. */
@FunctionalInterface
public interface ObjectReader {
    InputStream open(String bucket, String key) throws IOException;
}