package gov.openpto.ingest.transform;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;

/** Streams records as one JSON array ({@code [\n{...},\n{...}\n]}) without holding them in memory. */
public final class JsonArrayRecordWriter implements RecordSink, Closeable {

    private static final byte[] OPEN = "[\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEPARATOR = ",\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CLOSE = "\n]\n".getBytes(StandardCharsets.UTF_8);

    private final OutputStream out;
    private final ObjectMapper mapper;
    private long written;
    private boolean closed;

    public JsonArrayRecordWriter(OutputStream out) {
        this(out, Json.mapper());
    }

    public JsonArrayRecordWriter(OutputStream out, ObjectMapper mapper) {
        this.out = out;
        this.mapper = mapper;
    }

    @Override
    public void accept(Object record) throws IOException {
        out.write(written == 0 ? OPEN : SEPARATOR);
        out.write(mapper.writeValueAsBytes(record));
        written++;
    }

    public long written() {
        return written;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (written == 0) {
            out.write(OPEN);
        }
        out.write(CLOSE);
        out.flush();
        out.close();
    }
}