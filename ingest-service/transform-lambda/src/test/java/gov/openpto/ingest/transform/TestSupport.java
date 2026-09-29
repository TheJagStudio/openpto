package gov.openpto.ingest.transform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the transformer tests. */
public final class TestSupport {

    private TestSupport() {
    }

    public static Path sample(String name) {
        String dir = System.getProperty("samples.dir", "../samples");
        return Path.of(dir, name);
    }

    public static Result run(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return run(in);
        }
    }

    public static Result run(String xml) throws IOException {
        return run(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static Result run(InputStream in) throws IOException {
        List<Object> records = new ArrayList<>();
        TransformSummary summary = new Transformer().transform(in, records::add, TransformOptions.forJob("job-1"));
        return new Result(summary, records);
    }

    public record Result(TransformSummary summary, List<Object> records) {
        @SuppressWarnings("unchecked")
        public <T> T record(int i) {
            return (T) records.get(i);
        }
    }
}