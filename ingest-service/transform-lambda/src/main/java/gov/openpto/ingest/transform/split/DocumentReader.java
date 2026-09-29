package gov.openpto.ingest.transform.split;

import gov.openpto.ingest.transform.xml.XmlTreeBuilder;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link Reader} over exactly ONE XML document of a concatenated bulk file. It ends (returns -1) at the
 * line where the next document starts, which stays buffered in the splitter for the next document.
 *
 * <p>USPTO files carry per-document {@code <!DOCTYPE>} lines, and legacy PATDOC files use SGML-style
 * declarations ({@code PUBLIC "id" [ ... ]} without a system id) that are not well-formed XML. The DOCTYPE
 * (including any internal subset) is therefore removed before the parser sees the document, and named entity
 * references other than the five XML built-ins are replaced from a small allow-list of character entities
 * (unknown ones are dropped). Nothing is ever resolved from a DTD, the file system or the network.
 */
public final class DocumentReader extends Reader {

    private static final Pattern ELEMENT_START = Pattern.compile("<[A-Za-z_]");
    private static final Pattern ENTITY_REF = Pattern.compile("&([A-Za-z_][A-Za-z0-9._-]*);");
    private static final Set<String> BUILT_IN_ENTITIES = Set.of("amp", "lt", "gt", "quot", "apos");
    private static final int HEAD_CAPACITY = 16 * 1024;

    private final ConcatenatedXmlSplitter splitter;
    private final int ordinal;
    private final int startLine;
    private final long maxChars;
    private final StringBuilder head = new StringBuilder();

    private String buffer;
    private int position;
    private boolean contentStarted;
    private boolean inDoctype;
    private boolean inSubset;
    private char quote;
    private boolean finished;
    private long chars;

    DocumentReader(ConcatenatedXmlSplitter splitter, int ordinal, int startLine, String firstLine, long maxChars) {
        this.splitter = splitter;
        this.ordinal = ordinal;
        this.startLine = startLine;
        this.maxChars = maxChars;
        accept(firstLine);
    }

    /** 1-based position of this document in the file. */
    public int ordinal() {
        return ordinal;
    }

    /** 1-based line number where this document starts. */
    public int startLine() {
        return startLine;
    }

    /** First few KB of the document, used to identify a document that fails to parse. */
    public String head() {
        return head.toString();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        while (buffer == null || position >= buffer.length()) {
            if (finished || !advance()) {
                return -1;
            }
        }
        int n = Math.min(len, buffer.length() - position);
        buffer.getChars(position, position + n, cbuf, off);
        position += n;
        return n;
    }

    /** Skips whatever is left of this document so the splitter is positioned at the next one. */
    public void drain() throws IOException {
        while (!finished) {
            String line = splitter.readLine();
            if (line == null || isBoundary(line)) {
                finish(line);
            } else {
                trackContent(line);
            }
        }
        buffer = null;
    }

    private boolean advance() throws IOException {
        String line = splitter.readLine();
        if (line == null || isBoundary(line)) {
            finish(line);
            return false;
        }
        accept(line);
        return true;
    }

    private void finish(String nextDocumentFirstLine) {
        finished = true;
        splitter.pushBack(nextDocumentFirstLine);
    }

    private void accept(String line) {
        String sanitized = sanitize(line);
        trackContent(line);
        chars += line.length() + 1L;
        if (chars > maxChars) {
            throw new DocumentTooLargeException("Document #" + ordinal + " exceeds " + maxChars + " characters");
        }
        if (head.length() < HEAD_CAPACITY) {
            head.append(line, 0, Math.min(line.length(), HEAD_CAPACITY - head.length())).append('\n');
        }
        buffer = sanitized + "\n";
        position = 0;
    }

    private String sanitize(String line) {
        String result = (inDoctype || !contentStarted) ? stripDoctype(line) : line;
        if (result.indexOf('&') < 0) {
            return result;
        }
        Matcher m = ENTITY_REF.matcher(result);
        StringBuilder sb = new StringBuilder(result.length());
        while (m.find()) {
            String name = m.group(1);
            String replacement = BUILT_IN_ENTITIES.contains(name) ? m.group() : XmlTreeBuilder.resolveEntity(name);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Removes a (possibly multi-line) DOCTYPE declaration, keeping the line structure intact. */
    private String stripDoctype(String line) {
        int i;
        StringBuilder out;
        if (inDoctype) {
            out = new StringBuilder();
            i = 0;
        } else {
            int start = line.indexOf("<!DOCTYPE");
            if (start < 0) {
                return line;
            }
            out = new StringBuilder(line.substring(0, start));
            inDoctype = true;
            inSubset = false;
            quote = 0;
            i = start + "<!DOCTYPE".length();
        }
        for (; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '[') {
                inSubset = true;
            } else if (ch == ']' && inSubset) {
                inSubset = false;
            } else if (ch == '>' && !inSubset) {
                inDoctype = false;
                return out.append(line, i + 1, line.length()).toString();
            }
        }
        return out.toString();
    }

    private void trackContent(String line) {
        if (!contentStarted && ELEMENT_START.matcher(line).find()) {
            contentStarted = true;
        }
    }

    /**
     * A new document starts at an XML declaration, or at a DOCTYPE once the current document already has
     * element content (legacy files where documents are not preceded by an XML declaration).
     */
    private boolean isBoundary(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("<?xml") && trimmed.length() > 5
                && (Character.isWhitespace(trimmed.charAt(5)) || trimmed.charAt(5) == '?')) {
            return true;
        }
        return contentStarted && trimmed.startsWith("<!DOCTYPE");
    }

    @Override
    public void close() {
        // lifecycle is owned by the splitter
    }

    /** Thrown when one document exceeds the configured size; the rest of the file is still processed. */
    public static final class DocumentTooLargeException extends RuntimeException {
        DocumentTooLargeException(String message) {
            super(message);
        }
    }
}
