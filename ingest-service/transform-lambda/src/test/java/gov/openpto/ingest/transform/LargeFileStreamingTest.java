package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Generates 5,000 concatenated grant documents on the fly (never materialised) and streams them through. */
class LargeFileStreamingTest {

    private static final int DOCUMENTS = 5_000;

    private static String document(int i) {
        String number = String.valueOf(13_000_000 + i);
        StringBuilder claims = new StringBuilder();
        for (int c = 1; c <= 20; c++) {
            claims.append("<claim id=\"CLM-%05d\" num=\"%05d\"><claim-text>%d. A widget %s of <claim-ref idref=\"CLM-00001\">claim 1</claim-ref> with padding text to make the document realistically sized.</claim-text></claim>\n"
                    .formatted(c, c, c, "variant " + c));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE us-patent-grant SYSTEM "us-patent-grant-v47-2022-02-17.dtd" [ ]>
                <us-patent-grant lang="EN"><us-bibliographic-data-grant>
                <publication-reference><document-id><country>US</country><doc-number>%s</doc-number><kind>B2</kind><date>20250107</date></document-id></publication-reference>
                <application-reference appl-type="utility"><document-id><country>US</country><doc-number>17%06d</doc-number><date>20210101</date></document-id></application-reference>
                <classifications-cpc><main-cpc><classification-cpc><section>G</section><class>06</class><subclass>F</subclass><main-group>1</main-group><subgroup>00</subgroup></classification-cpc></main-cpc></classifications-cpc>
                <invention-title>Synthetic invention %d</invention-title>
                <us-parties><inventors><inventor><addressbook><last-name>Doe</last-name><first-name>Jane</first-name><address><city>Austin</city><state>TX</state><country>US</country></address></addressbook></inventor></inventors></us-parties>
                </us-bibliographic-data-grant>
                <abstract><p>Synthetic abstract for document %d used by the streaming test.</p></abstract>
                <claims>
                %s</claims>
                </us-patent-grant>
                """.formatted(number, i, i, i, claims);
    }

    @Test
    void transform_5000ConcatenatedDocuments_streamsWithBoundedMemoryAndTime() throws Exception {
        AtomicLong bytes = new AtomicLong();
        Enumeration<InputStream> parts = new Enumeration<>() {
            private int next;

            @Override
            public boolean hasMoreElements() {
                return next < DOCUMENTS;
            }

            @Override
            public InputStream nextElement() {
                byte[] doc = document(next++).getBytes(StandardCharsets.UTF_8);
                bytes.addAndGet(doc.length);
                return new ByteArrayInputStream(doc);
            }
        };

        Runtime rt = Runtime.getRuntime();
        System.gc();
        long baseline = rt.totalMemory() - rt.freeMemory();
        long[] peak = {0};
        AtomicLong jsonBytes = new AtomicLong();
        OutputStream counting = new OutputStream() {
            @Override
            public void write(int b) {
                jsonBytes.incrementAndGet();
            }

            @Override
            public void write(byte[] b, int off, int len) {
                jsonBytes.addAndGet(len);
            }
        };

        long start = System.nanoTime();
        TransformSummary summary;
        try (JsonArrayRecordWriter writer = new JsonArrayRecordWriter(counting)) {
            int[] seen = {0};
            summary = new Transformer().transform(new SequenceInputStream(parts), record -> {
                writer.accept(record);
                if (++seen[0] % 1000 == 0) {
                    System.gc(); // measure what is actually retained, not collectable garbage
                    peak[0] = Math.max(peak[0], rt.totalMemory() - rt.freeMemory());
                }
            }, TransformOptions.forJob(null));
        }
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        assertThat(summary.documents()).isEqualTo(DOCUMENTS);
        assertThat(summary.recordsOk()).isEqualTo(DOCUMENTS);
        assertThat(summary.recordsFailed()).isZero();
        assertThat(bytes.get()).isGreaterThan(15L * 1024 * 1024); // > 15 MB of XML went through
        assertThat(jsonBytes.get()).isGreaterThan(10L * 1024 * 1024);
        assertThat(seconds).isLessThan(60);
        // retained memory must not grow with the input (records and documents are not kept)
        assertThat(peak[0] - baseline).isLessThan(32L * 1024 * 1024).isLessThan(bytes.get() / 2);
    }
}