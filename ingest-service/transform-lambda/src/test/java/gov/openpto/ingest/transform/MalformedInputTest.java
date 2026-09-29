package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.PatentRecord;
import org.junit.jupiter.api.Test;

class MalformedInputTest {

    @Test
    void malformedSample_goodDocumentLoaded_brokenDocumentReported() throws Exception {
        TestSupport.Result result = TestSupport.run(TestSupport.sample("malformed-sample.xml"));

        assertThat(result.summary().documents()).isEqualTo(2);
        assertThat(result.summary().recordsTotal()).isEqualTo(2);
        assertThat(result.summary().recordsOk()).isEqualTo(1);
        assertThat(result.summary().recordsFailed()).isEqualTo(1);
        assertThat(((PatentRecord) result.record(0)).patentNumber()).isEqualTo("US12345611B2");
        assertThat(result.summary().errors()).singleElement().satisfies(e -> {
            assertThat(e.index()).isEqualTo(1);
            assertThat(e.identifier()).isEqualTo("12345612");
            assertThat(e.message()).startsWith("Malformed XML in document #2");
        });
    }

    @Test
    void brokenFirstDocument_doesNotAbortFollowingDocuments() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <us-patent-grant><unclosed>
                <?xml version="1.0"?>
                <us-patent-grant><us-bibliographic-data-grant>
                <publication-reference><document-id><doc-number>12000001</doc-number><kind>B2</kind><date>20250101</date></document-id></publication-reference>
                <application-reference appl-type="design"><document-id><doc-number>29000001</doc-number><date>20230101</date></document-id></application-reference>
                <invention-title>Chair</invention-title>
                </us-bibliographic-data-grant></us-patent-grant>
                """;
        TestSupport.Result result = TestSupport.run(xml);

        assertThat(result.summary().recordsOk()).isEqualTo(1);
        assertThat(result.summary().recordsFailed()).isEqualTo(1);
        PatentRecord design = result.record(0);
        assertThat(design.type()).isEqualTo("DESIGN");
        assertThat(design.expirationDate()).isEqualTo(design.grantDate().plusYears(15));
        assertThat(design.claims()).isEmpty();
        assertThat(design.primaryCpc()).isNull();
    }

    @Test
    void missingRequiredFields_areMappingErrorsWithIdentifier() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <us-patent-grant><us-bibliographic-data-grant>
                <publication-reference><document-id><doc-number>12000002</doc-number><kind>B1</kind></document-id></publication-reference>
                <application-reference><document-id><doc-number>17000002</doc-number><date>20230101</date></document-id></application-reference>
                </us-bibliographic-data-grant></us-patent-grant>
                <?xml version="1.0"?>
                <us-patent-grant><us-bibliographic-data-grant>
                <publication-reference><document-id><doc-number>12000003</doc-number></document-id></publication-reference>
                <invention-title>No filing date</invention-title>
                </us-bibliographic-data-grant></us-patent-grant>
                <?xml version="1.0"?>
                <us-patent-grant><nothing/></us-patent-grant>
                <?xml version="1.0"?>
                <PATDOC><SDOBI><B100><B110><DNUM><PDAT>06000001</PDAT></DNUM></B110></B100></SDOBI></PATDOC>
                <?xml version="1.0"?>
                <PATDOC><other/></PATDOC>
                """;
        TestSupport.Result result = TestSupport.run(xml);

        assertThat(result.summary().recordsOk()).isZero();
        assertThat(result.summary().errors()).extracting(RecordError::identifier)
                .containsExactly("US12000002B1", "US12000003", null, "US6000001", null);
        assertThat(result.summary().errors()).extracting(RecordError::message)
                .containsExactly("Missing required field: invention-title",
                        "Missing required field: application-reference/document-id/date",
                        "Missing <us-bibliographic-data-grant>",
                        "Missing required field: B220 filing date",
                        "Missing <SDOBI> bibliographic section");
    }

    @Test
    void unsupportedRootAndMixedFormats_areReported() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <something-else/>
                <?xml version="1.0"?>
                <PATDOC><SDOBI><B100><B110><DNUM><PDAT>06000009</PDAT></DNUM></B110></B100>
                <B200><B220><DATE><PDAT>20000101</PDAT></DATE></B220></B200><B500><B540><STEXT><PDAT>T</PDAT></STEXT></B540></B500></SDOBI></PATDOC>
                <?xml version="1.0"?>
                <trademark-applications-daily><serial-number>97000009</serial-number></trademark-applications-daily>
                <?xml version="1.0"?>
                <!-- only a comment -->
                """;
        TestSupport.Result result = TestSupport.run(xml);

        assertThat(result.summary().format()).isEqualTo(DocumentFormat.PATDOC_LEGACY);
        assertThat(result.summary().recordsOk()).isEqualTo(1);
        assertThat(result.summary().errors()).hasSize(3);
        assertThat(result.summary().errors().get(0).message()).contains("Unsupported document type <something-else>");
        assertThat(result.summary().errors().get(1).message()).contains("cannot be mixed");
        assertThat(result.summary().errors().get(1).identifier()).isEqualTo("97000009");
        assertThat(result.summary().errors().get(2).message()).contains("document #4")
                .containsAnyOf("has no root element", "Premature end of file");
    }

    @Test
    void errorsAreCappedButAllCounted() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n<bad/>\n".repeat(20);
        java.util.List<Object> sink = new java.util.ArrayList<>();
        TransformSummary summary = new Transformer().transform(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sink::add, new TransformOptions(null, 5));

        assertThat(summary.recordsFailed()).isEqualTo(20);
        assertThat(summary.errors()).hasSize(5);
        assertThat(summary.format()).isEqualTo(DocumentFormat.UNKNOWN);
    }

    @Test
    void invalidUtf8Bytes_areReplacedNotFatal() throws Exception {
        byte[] prefix = "<?xml version=\"1.0\"?>\n<trademark-applications-daily><case-file><serial-number>97000010</serial-number><case-file-header><filing-date>20240101</filing-date><mark-identification>CAF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] suffix = "</mark-identification></case-file-header></case-file></trademark-applications-daily>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[prefix.length + 1 + suffix.length];
        System.arraycopy(prefix, 0, all, 0, prefix.length);
        all[prefix.length] = (byte) 0xC3; // truncated multi-byte sequence
        System.arraycopy(suffix, 0, all, prefix.length + 1, suffix.length);

        TestSupport.Result result = TestSupport.run(new java.io.ByteArrayInputStream(all));

        assertThat(result.summary().recordsOk()).isEqualTo(1);
    }
}