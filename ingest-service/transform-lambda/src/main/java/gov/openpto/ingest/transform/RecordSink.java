package gov.openpto.ingest.transform;

import java.io.IOException;

/** Receives successfully transformed records, one at a time (streaming). */
@FunctionalInterface
public interface RecordSink {
    void accept(Object record) throws IOException;
}