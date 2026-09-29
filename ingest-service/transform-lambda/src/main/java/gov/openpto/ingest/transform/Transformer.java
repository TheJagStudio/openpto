package gov.openpto.ingest.transform;

import gov.openpto.ingest.transform.mapper.RecordMapper;
import gov.openpto.ingest.transform.split.ConcatenatedXmlSplitter;
import gov.openpto.ingest.transform.split.DocumentReader;
import gov.openpto.ingest.transform.xml.SecureXmlFactory;
import gov.openpto.ingest.transform.xml.XmlNode;
import gov.openpto.ingest.transform.xml.XmlTreeBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streams a (possibly concatenated) USPTO bulk XML file into neutral JSON records.
 *
 * <p>Memory is bounded by the size of ONE record: documents are split line by line, and trademark daily
 * files (one huge document) are read {@code case-file} by {@code case-file}. A bad document or record is
 * reported as a {@link RecordError} and never aborts the rest of the file.
 */
public final class Transformer {

    private static final Pattern[] IDENTIFIER_PATTERNS = {
            Pattern.compile("<doc-number>\\s*([^<\\s]+)\\s*</doc-number>"),
            Pattern.compile("<B110>\\s*<DNUM>\\s*<PDAT>\\s*([^<\\s]+)"),
            Pattern.compile("<serial-number>\\s*(\\d+)"),
    };

    private final XMLInputFactory factory = SecureXmlFactory.newInputFactory();
    private final long maxDocumentChars;
    private final Map<DocumentFormat, RecordMapper> mappers = new EnumMap<>(DocumentFormat.class);

    public Transformer() {
        this(ConcatenatedXmlSplitter.DEFAULT_MAX_DOCUMENT_CHARS);
    }

    public Transformer(long maxDocumentChars) {
        this.maxDocumentChars = maxDocumentChars;
        for (DocumentFormat format : DocumentFormat.values()) {
            if (format != DocumentFormat.UNKNOWN) {
                mappers.put(format, RecordMapper.forFormat(format));
            }
        }
    }

    /**
     * @throws IOException only for I/O failures of the input or the sink (a file-level failure);
     *                     content problems become {@link RecordError}s.
     */
    public TransformSummary transform(InputStream input, RecordSink sink, TransformOptions options) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        State state = new State(options);
        try (ConcatenatedXmlSplitter splitter = new ConcatenatedXmlSplitter(
                new BufferedReader(new InputStreamReader(input, decoder), 64 * 1024), maxDocumentChars)) {
            DocumentReader document;
            while ((document = splitter.next()) != null) {
                processDocument(document, sink, state);
            }
            state.documents = splitter.documentsSeen();
        }
        return state.summary();
    }

    private void processDocument(DocumentReader document, RecordSink sink, State state) throws IOException {
        XMLStreamReader xml = null;
        try {
            xml = factory.createXMLStreamReader(document);
            String root = advanceToRootElement(xml);
            if (root == null) {
                state.fail(identifier(document), "Document #" + document.ordinal() + " (line " + document.startLine()
                        + ") has no root element");
                return;
            }
            DocumentFormat format = DocumentFormat.fromRootElement(root);
            if (format == DocumentFormat.UNKNOWN) {
                state.fail(identifier(document), "Unsupported document type <" + root + "> in document #"
                        + document.ordinal() + " (line " + document.startLine() + ")");
                return;
            }
            if (state.format == DocumentFormat.UNKNOWN) {
                state.format = format;
            } else if (state.format.target() != format.target()) {
                state.fail(identifier(document), "Document #" + document.ordinal() + " is " + format
                        + " but the file is " + state.format + "; patents and trademarks cannot be mixed");
                return;
            }
            RecordMapper mapper = mappers.get(format);
            if (format == DocumentFormat.TRADEMARK_DAILY) {
                processCaseFiles(xml, mapper, sink, state);
            } else {
                XmlNode node = XmlTreeBuilder.read(xml);
                emit(node, mapper, sink, state);
            }
        } catch (XMLStreamException | RuntimeException e) {
            if (e instanceof SinkFailure failure) {
                throw failure.getCause();
            }
            state.fail(identifier(document), "Malformed XML in document #" + document.ordinal() + " (starting line "
                    + document.startLine() + "): " + cleanMessage(e));
        } finally {
            closeQuietly(xml);
        }
    }

    private void processCaseFiles(XMLStreamReader xml, RecordMapper mapper, RecordSink sink, State state)
            throws XMLStreamException {
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT && "case-file".equals(xml.getLocalName())) {
                emit(XmlTreeBuilder.read(xml), mapper, sink, state);
            }
        }
    }

    private static void emit(XmlNode node, RecordMapper mapper, RecordSink sink, State state) {
        Object record;
        try {
            record = mapper.map(node, state.options.ingestJobId());
        } catch (RuntimeException e) {
            String id = e instanceof MappingException me && me.identifier() != null ? me.identifier() : safeIdentify(mapper, node);
            state.fail(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return;
        }
        try {
            sink.accept(record);
        } catch (IOException e) {
            throw new SinkFailure(e);
        }
        state.ok();
    }

    private static String safeIdentify(RecordMapper mapper, XmlNode node) {
        try {
            return mapper.identify(node);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String advanceToRootElement(XMLStreamReader xml) throws XMLStreamException {
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT) {
                return xml.getLocalName();
            }
        }
        return null;
    }

    static String identifier(DocumentReader document) {
        String head = document.head();
        for (Pattern pattern : IDENTIFIER_PATTERNS) {
            Matcher m = pattern.matcher(head);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String cleanMessage(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        // JDK StAX prefixes "ParseError at [row,col]:[r,c]\nMessage: ..." — keep it on one line
        return message.replace('\n', ' ').replace("\r", "").trim();
    }

    private static void closeQuietly(XMLStreamReader xml) {
        if (xml != null) {
            try {
                xml.close();
            } catch (XMLStreamException ignored) {
                // nothing to release
            }
        }
    }

    /** Wraps a sink I/O failure so it is not mistaken for a content error. */
    private static final class SinkFailure extends RuntimeException {
        SinkFailure(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }

    private static final class State {
        private final TransformOptions options;
        private final List<RecordError> errors = new ArrayList<>();
        private DocumentFormat format = DocumentFormat.UNKNOWN;
        private int index;
        private int ok;
        private int failed;
        private int documents;

        State(TransformOptions options) {
            this.options = options;
        }

        void ok() {
            index++;
            ok++;
        }

        void fail(String identifier, String message) {
            if (errors.size() < options.maxErrors()) {
                errors.add(new RecordError(index, identifier, message));
            }
            index++;
            failed++;
        }

        TransformSummary summary() {
            return new TransformSummary(format, documents, ok + failed, ok, failed, List.copyOf(errors));
        }
    }
}
