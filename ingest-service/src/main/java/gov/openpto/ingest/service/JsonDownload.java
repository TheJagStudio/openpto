package gov.openpto.ingest.service;

import java.io.IOException;
import java.io.OutputStream;

/** A validated, ready-to-stream JSON download. */
public record JsonDownload(String fileName, long sizeBytes, Writer writer) {

    @FunctionalInterface
    public interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }
}