package gov.openpto.ingest.transform.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConcatenatedXmlSplitterTest {

    private static List<String> split(String input) throws IOException {
        return split(input, ConcatenatedXmlSplitter.DEFAULT_MAX_DOCUMENT_CHARS);
    }

    private static List<String> split(String input, long max) throws IOException {
        List<String> docs = new ArrayList<>();
        try (ConcatenatedXmlSplitter splitter = new ConcatenatedXmlSplitter(new StringReader(input), max)) {
            DocumentReader doc;
            while ((doc = splitter.next()) != null) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[7];
                int n;
                while ((n = doc.read(buf, 0, buf.length)) != -1) {
                    sb.append(buf, 0, n);
                }
                docs.add(sb.toString().trim());
            }
        }
        return docs;
    }

    @Test
    void split_xmlDeclarationsStartNewDocuments() throws IOException {
        List<String> docs = split("""
                <?xml version="1.0"?>
                <!DOCTYPE a SYSTEM "a.dtd" [ ]>
                <a>1</a>
                <?xml version="1.0"?>
                <!DOCTYPE a SYSTEM "a.dtd" [ ]>
                <a>2</a>
                """);

        assertThat(docs).containsExactly("<?xml version=\"1.0\"?>\n\n<a>1</a>", "<?xml version=\"1.0\"?>\n\n<a>2</a>");
    }

    @Test
    void read_stripsSgmlStyleDoctypeAndReplacesNamedEntities() throws IOException {
        List<String> docs = split("""
                <?xml version="1.0"?>
                <!DOCTYPE PATDOC PUBLIC "-//USPTO//DTD ST.32//EN" [
                <!ENTITY img SYSTEM "a.tif" NDATA TIF>
                <!ENTITY note "a > b ] c">
                ]><PATDOC>x &mdash; y &amp; z &unknown; &#x25;</PATDOC>
                """);

        assertThat(docs).containsExactly("<?xml version=\"1.0\"?>\n\n\n\n<PATDOC>x \u2014 y &amp; z  &#x25;</PATDOC>");
    }

    @Test
    void split_doctypeWithoutDeclarationStartsNewDocumentAfterContent() throws IOException {
        List<String> docs = split("""
                <!DOCTYPE PATDOC PUBLIC "x" [
                <!ENTITY img SYSTEM "a.tif" NDATA TIF>
                ]>
                <PATDOC>1</PATDOC>
                <!DOCTYPE PATDOC PUBLIC "x" [ ]>
                <PATDOC>2</PATDOC>
                """);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0)).doesNotContain("<!ENTITY").endsWith("<PATDOC>1</PATDOC>");
        assertThat(docs.get(1)).isEqualTo("<PATDOC>2</PATDOC>");
    }

    @Test
    void split_declarationGluedToPreviousLine_isSplitOff() throws IOException {
        List<String> docs = split("<?xml version=\"1.0\"?><a>1</a><?xml version=\"1.0\"?><a>2</a><?xml version=\"1.0\"?><a>3</a>");

        assertThat(docs).containsExactly("<?xml version=\"1.0\"?><a>1</a>", "<?xml version=\"1.0\"?><a>2</a>",
                "<?xml version=\"1.0\"?><a>3</a>");
    }

    @Test
    void split_bomBlankLinesAndIndentedDeclaration() throws IOException {
        List<String> docs = split("\uFEFF\n\n   <?xml version=\"1.0\"?>\n<a/>\n\n\n<?xml version=\"1.0\"?>\n<b/>\n\n");

        assertThat(docs).containsExactly("<?xml version=\"1.0\"?>\n<a/>", "<?xml version=\"1.0\"?>\n<b/>");
    }

    @Test
    void split_unreadRemainderIsDrainedWhenMovingOn() throws IOException {
        try (ConcatenatedXmlSplitter splitter = new ConcatenatedXmlSplitter(new StringReader(
                "<?xml version=\"1.0\"?>\n<a>\n<x/>\n</a>\n<?xml version=\"1.0\"?>\n<b/>\n"))) {
            DocumentReader first = splitter.next();
            assertThat(first.ordinal()).isEqualTo(1);
            assertThat(first.startLine()).isEqualTo(1);
            DocumentReader second = splitter.next();
            assertThat(second.ordinal()).isEqualTo(2);
            assertThat(second.startLine()).isEqualTo(5);
            assertThat(second.head()).startsWith("<?xml");
            assertThat(splitter.next()).isNull();
            assertThat(splitter.documentsSeen()).isEqualTo(2);
        }
    }

    @Test
    void split_emptyInput_hasNoDocuments() throws IOException {
        assertThat(split("")).isEmpty();
        assertThat(split("\n  \n")).isEmpty();
    }

    @Test
    void split_documentLargerThanLimit_throws() {
        String big = "<?xml version=\"1.0\"?>\n<a>\n" + "<x>0123456789</x>\n".repeat(20) + "</a>\n";

        assertThatThrownBy(() -> split(big, 100))
                .isInstanceOf(DocumentReader.DocumentTooLargeException.class)
                .hasMessageContaining("exceeds 100");
    }

    @Test
    void read_zeroLength_returnsZero() throws IOException {
        try (ConcatenatedXmlSplitter splitter = new ConcatenatedXmlSplitter(new StringReader("<a/>"))) {
            DocumentReader doc = splitter.next();
            assertThat(doc.read(new char[1], 0, 0)).isZero();
            doc.close();
        }
    }
}