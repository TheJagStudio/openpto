package gov.openpto.ingest.transform.xml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Builds an {@link XmlNode} subtree for the element the StAX cursor currently points at. */
public final class XmlTreeBuilder {

    /** Hard cap on elements per record so a hostile document cannot exhaust memory. */
    public static final int MAX_ELEMENTS_PER_RECORD = 500_000;

    private static final Map<String, String> CHARACTER_ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("mdash", "—"), Map.entry("ndash", "–"), Map.entry("lsquo", "‘"),
            Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
            Map.entry("deg", "°"), Map.entry("plusmn", "±"), Map.entry("times", "×"),
            Map.entry("micro", "µ"), Map.entry("bull", "•"), Map.entry("hellip", "…"),
            Map.entry("trade", "™"), Map.entry("reg", "®"), Map.entry("copy", "©"),
            Map.entry("middot", "·"), Map.entry("frac12", "½"), Map.entry("alpha", "α"),
            Map.entry("beta", "β"), Map.entry("gamma", "γ"), Map.entry("delta", "δ"),
            Map.entry("mu", "μ"), Map.entry("Omega", "Ω"), Map.entry("le", "≤"),
            Map.entry("ge", "≥"));

    private XmlTreeBuilder() {
    }

    /** Resolves an entity name from the allow-list; unknown (possibly malicious) entities resolve to "". */
    public static String resolveEntity(String name) {
        return name == null ? "" : CHARACTER_ENTITIES.getOrDefault(name, "");
    }

    /**
     * Reads the element at the cursor (which must be on {@code START_ELEMENT}) including all descendants.
     * On return the cursor is positioned on the matching {@code END_ELEMENT}.
     */
    public static XmlNode read(XMLStreamReader reader) throws XMLStreamException {
        if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException("Cursor must be on START_ELEMENT");
        }
        XmlNode root = startElement(reader);
        Deque<XmlNode> stack = new ArrayDeque<>();
        stack.push(root);
        int elements = 1;
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if (++elements > MAX_ELEMENTS_PER_RECORD) {
                        throw new XMLStreamException("Record exceeds " + MAX_ELEMENTS_PER_RECORD + " elements");
                    }
                    XmlNode child = startElement(reader);
                    stack.peek().add(child);
                    stack.push(child);
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    stack.pop();
                    if (stack.isEmpty()) {
                        return root;
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
                        stack.peek().appendText(reader.getText());
                case XMLStreamConstants.ENTITY_REFERENCE ->
                        stack.peek().appendText(resolveEntity(reader.getLocalName()));
                default -> {
                    // comments, processing instructions: ignored
                }
            }
        }
        throw new XMLStreamException("Unexpected end of document inside <" + root.name() + ">");
    }

    private static XmlNode startElement(XMLStreamReader reader) {
        int count = reader.getAttributeCount();
        Map<String, String> attributes = count == 0 ? Map.of() : new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
        return XmlNode.element(reader.getLocalName(), attributes);
    }
}
