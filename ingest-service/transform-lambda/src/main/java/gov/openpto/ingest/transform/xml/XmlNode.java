package gov.openpto.ingest.transform.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny immutable-after-build element tree for ONE record (a patent document or a trademark case-file).
 * Whole bulk files are never materialised; see {@link XmlTreeBuilder}.
 */
public final class XmlNode {

    private final String name;
    private final Map<String, String> attributes;
    private final List<XmlNode> children;
    private final StringBuilder text;

    private XmlNode(String name, Map<String, String> attributes, StringBuilder text) {
        this.name = name;
        this.attributes = attributes;
        this.children = text == null ? new ArrayList<>() : List.of();
        this.text = text;
    }

    public static XmlNode element(String name, Map<String, String> attributes) {
        return new XmlNode(name, attributes == null ? Map.of() : new LinkedHashMap<>(attributes), null);
    }

    public static XmlNode textNode(String value) {
        return new XmlNode(null, Map.of(), new StringBuilder(value));
    }

    void add(XmlNode child) {
        children.add(child);
    }

    void appendText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!children.isEmpty()) {
            XmlNode last = children.get(children.size() - 1);
            if (last.isText()) {
                last.text.append(value);
                return;
            }
        }
        children.add(textNode(value));
    }

    public String name() {
        return name;
    }

    public boolean isText() {
        return text != null;
    }

    public String attr(String attribute) {
        return attributes.get(attribute);
    }

    public List<XmlNode> elements() {
        List<XmlNode> result = new ArrayList<>();
        for (XmlNode c : children) {
            if (!c.isText()) {
                result.add(c);
            }
        }
        return result;
    }

    /** First direct child element with the given name, or {@code null}. */
    public XmlNode child(String childName) {
        for (XmlNode c : children) {
            if (!c.isText() && c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }

    /** All direct child elements with the given name. */
    public List<XmlNode> children(String childName) {
        List<XmlNode> result = new ArrayList<>();
        for (XmlNode c : children) {
            if (!c.isText() && c.name.equals(childName)) {
                result.add(c);
            }
        }
        return result;
    }

    /** Follows a slash separated path of direct children, e.g. {@code "publication-reference/document-id"}. */
    public XmlNode find(String path) {
        XmlNode current = this;
        for (String part : path.split("/")) {
            if (current == null) {
                return null;
            }
            current = current.child(part);
        }
        return current;
    }

    /** All elements at the end of a path, e.g. {@code find("inventors/inventor")}. */
    public List<XmlNode> findAll(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return children(path);
        }
        XmlNode parent = find(path.substring(0, slash));
        return parent == null ? List.of() : parent.children(path.substring(slash + 1));
    }

    /** Depth-first search for all descendant elements with the given name. */
    public List<XmlNode> descendants(String elementName) {
        List<XmlNode> result = new ArrayList<>();
        collect(this, elementName, result);
        return result;
    }

    /** First descendant element with the given name, or {@code null}. */
    public XmlNode firstDescendant(String elementName) {
        for (XmlNode c : children) {
            if (c.isText()) {
                continue;
            }
            if (c.name.equals(elementName)) {
                return c;
            }
            XmlNode found = c.firstDescendant(elementName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(XmlNode node, String elementName, List<XmlNode> out) {
        for (XmlNode c : node.children) {
            if (c.isText()) {
                continue;
            }
            if (c.name.equals(elementName)) {
                out.add(c);
            }
            collect(c, elementName, out);
        }
    }

    /** All descendant text, whitespace-normalised (runs collapsed to one space, trimmed). */
    public String text() {
        StringBuilder sb = new StringBuilder();
        appendAllText(this, sb);
        return normalize(sb);
    }

    /** Text of the element at {@code path} or {@code null} when missing or blank. */
    public String textOf(String path) {
        XmlNode node = find(path);
        if (node == null) {
            return null;
        }
        String value = node.text();
        return value.isEmpty() ? null : value;
    }

    private static void appendAllText(XmlNode node, StringBuilder sb) {
        if (node.isText()) {
            sb.append(node.text);
            return;
        }
        for (XmlNode c : node.children) {
            appendAllText(c, sb);
        }
    }

    static String normalize(CharSequence raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || ch == ' ') {
                space = true;
            } else {
                if (space && !sb.isEmpty()) {
                    sb.append(' ');
                }
                space = false;
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String toString() {
        return isText() ? "#text" : "<" + name + ">";
    }
}
