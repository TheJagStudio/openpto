package gov.openpto.ingest.transform.split;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Streams a USPTO bulk file in which many XML documents are simply concatenated
 * ({@code <?xml ...?><!DOCTYPE ...><us-patent-grant>...</us-patent-grant><?xml ...?>...}).
 *
 * <p>Only one line is buffered at a time; each document is exposed as a {@link DocumentReader} that can be
 * fed straight into a StAX parser. Documents may start with an XML declaration, or (legacy PATDOC streams)
 * directly with a {@code <!DOCTYPE>}. An XML declaration glued onto the end of the previous line is split off.
 */
public final class ConcatenatedXmlSplitter implements Closeable {

    public static final long DEFAULT_MAX_DOCUMENT_CHARS = 256L * 1024 * 1024;

    private final BufferedReader source;
    private final long maxDocumentChars;
    private final Deque<String> pending = new ArrayDeque<>();
    private int lineNumber;
    private int documents;
    private DocumentReader current;
    private boolean firstLine = true;

    public ConcatenatedXmlSplitter(Reader source) {
        this(source, DEFAULT_MAX_DOCUMENT_CHARS);
    }

    public ConcatenatedXmlSplitter(Reader source, long maxDocumentChars) {
        this.source = source instanceof BufferedReader br ? br : new BufferedReader(source, 64 * 1024);
        this.maxDocumentChars = maxDocumentChars;
    }

    /** Advances to the next document (draining the rest of the current one) or returns {@code null} at EOF. */
    public DocumentReader next() throws IOException {
        if (current != null) {
            current.drain();
            current = null;
        }
        String line = readLine();
        while (line != null && line.isBlank()) {
            line = readLine();
        }
        if (line == null) {
            return null;
        }
        current = new DocumentReader(this, ++documents, lineNumber, line.stripLeading(), maxDocumentChars);
        return current;
    }

    public int documentsSeen() {
        return documents;
    }

    /** Next logical line; a line holding {@code ...</root><?xml ...} is returned as two logical lines. */
    String readLine() throws IOException {
        if (!pending.isEmpty()) {
            return pending.pop();
        }
        String line = source.readLine();
        if (line == null) {
            return null;
        }
        lineNumber++;
        if (firstLine) {
            firstLine = false;
            if (!line.isEmpty() && line.charAt(0) == '﻿') {
                line = line.substring(1);
            }
        }
        int split = line.indexOf("<?xml", 1);
        if (split > 0 && !line.substring(0, split).isBlank()) {
            // queue the remainder (which itself may hold further declarations)
            Deque<String> parts = new ArrayDeque<>();
            String rest = line;
            while (split > 0 && !rest.substring(0, split).isBlank()) {
                parts.add(rest.substring(0, split));
                rest = rest.substring(split);
                split = rest.indexOf("<?xml", 1);
            }
            parts.add(rest);
            String first = parts.pop();
            while (!parts.isEmpty()) {
                pending.addLast(parts.pop());
            }
            return first;
        }
        return line;
    }

    void pushBack(String line) {
        if (line != null) {
            pending.push(line);
        }
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
