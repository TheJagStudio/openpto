package gov.openpto.ingest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import gov.openpto.ingest.transform.model.PatentRecord;
import gov.openpto.ingest.transform.xml.SecureXmlFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XxeProtectionTest {

    private static final String SECRET = "TOP-SECRET-7f3a9c";

    @TempDir
    Path tmp;

    private static String grantWithTitle(String doctype, String title) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                %s
                <us-patent-grant><us-bibliographic-data-grant>
                <publication-reference><document-id><doc-number>12999999</doc-number><kind>B2</kind><date>20250101</date></document-id></publication-reference>
                <application-reference appl-type="utility"><document-id><doc-number>17999999</doc-number><date>20220101</date></document-id></application-reference>
                <invention-title>%s</invention-title>
                </us-bibliographic-data-grant></us-patent-grant>
                """.formatted(doctype, title);
    }

    @Test
    void factory_hasDtdAndExternalEntitiesDisabled() {
        XMLInputFactory factory = SecureXmlFactory.newInputFactory();

        assertThat(factory.getProperty(XMLInputFactory.SUPPORT_DTD)).isEqualTo(Boolean.FALSE);
        assertThat(factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)).isEqualTo(Boolean.FALSE);
        assertThat(factory.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void secureFactoryAlone_doesNotResolveExternalEntity() throws Exception {
        // defence in depth: even if a DOCTYPE reached the parser, the factory must not read the file
        Path secret = Files.writeString(tmp.resolve("direct.txt"), SECRET);
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [ <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\"> ]><r>a&xxe;b</r>";
        StringBuilder text = new StringBuilder();
        try {
            var reader = SecureXmlFactory.newInputFactory().createXMLStreamReader(new java.io.StringReader(xml));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == javax.xml.stream.XMLStreamConstants.CHARACTERS) {
                    text.append(reader.getText());
                }
            }
        } catch (javax.xml.stream.XMLStreamException expected) {
            assertThat(expected.getMessage()).doesNotContain(SECRET);
        }
        assertThat(text.toString()).doesNotContain(SECRET);
    }

    @Test
    void externalFileEntity_isNotResolved() throws Exception {
        Path secret = Files.writeString(tmp.resolve("secret.txt"), SECRET);
        String doctype = "<!DOCTYPE us-patent-grant [ <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\"> ]>";

        TestSupport.Result result = TestSupport.run(grantWithTitle(doctype, "Title &xxe; end"));

        assertThat(result.records()).hasSize(1);
        PatentRecord record = result.record(0);
        assertThat(record.title()).isEqualTo("Title end").doesNotContain(SECRET);
        assertThat(Json.mapper().writeValueAsString(record)).doesNotContain(SECRET);
    }

    @Test
    void externalDtd_isNeverFetched() throws Exception {
        // a DTD that would inject the secret through a default entity if it were loaded
        Path dtd = Files.writeString(tmp.resolve("evil.dtd"), "<!ENTITY inject \"" + SECRET + "\">");
        String doctype = "<!DOCTYPE us-patent-grant SYSTEM \"" + dtd.toUri() + "\">";

        TestSupport.Result result = TestSupport.run(grantWithTitle(doctype, "A &inject; B"));

        assertThat(result.records()).hasSize(1);
        assertThat(((PatentRecord) result.record(0)).title()).isEqualTo("A B");
    }

    @Test
    void parameterEntityOutOfBandExfiltration_isNotResolved() throws Exception {
        Path secret = Files.writeString(tmp.resolve("secret2.txt"), SECRET);
        String doctype = "<!DOCTYPE us-patent-grant [ <!ENTITY % file SYSTEM \"" + secret.toUri()
                + "\"> <!ENTITY % dtd SYSTEM \"http://127.0.0.1:9/evil.dtd\"> %dtd; ]>";

        TestSupport.Result result = TestSupport.run(grantWithTitle(doctype, "Safe"));

        assertThat(result.summary().errors()).allSatisfy(e -> assertThat(e.message()).doesNotContain(SECRET));
        result.records().forEach(r -> assertThat(r.toString()).doesNotContain(SECRET));
    }

    @Test
    void billionLaughs_isNotExpanded() throws Exception {
        StringBuilder doctype = new StringBuilder("<!DOCTYPE us-patent-grant [ <!ENTITY lol \"lol\">");
        doctype.append("<!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">");
        for (int i = 2; i <= 9; i++) {
            doctype.append("<!ENTITY lol").append(i).append(" \"");
            doctype.append(("&lol" + (i - 1) + ";").repeat(10)).append("\">");
        }
        doctype.append(" ]>");

        long start = System.nanoTime();
        TestSupport.Result result = TestSupport.run(grantWithTitle(doctype.toString(), "&lol9;"));
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(ms).isLessThan(5_000);
        assertThat(result.summary().recordsTotal()).isEqualTo(1);
        result.records().forEach(r -> assertThat(r.toString()).doesNotContain("lollol"));
    }
}