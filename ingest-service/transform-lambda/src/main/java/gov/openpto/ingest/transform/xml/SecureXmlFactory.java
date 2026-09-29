package gov.openpto.ingest.transform.xml;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;

/**
 * Creates StAX factories hardened against XXE and entity-expansion attacks.
 *
 * <ul>
 *   <li>DTD processing is off: a {@code <!DOCTYPE>} is skipped, internal subsets are never evaluated,
 *       so no custom entity can be declared (no "billion laughs").</li>
 *   <li>External entities and external DTDs are never fetched (no file/network access).</li>
 *   <li>Entity references are reported instead of replaced; {@link XmlTreeBuilder} maps a small allow-list
 *       of well-known character entities (e.g. {@code &mdash;}) and drops everything else.</li>
 * </ul>
 */
public final class SecureXmlFactory {

    private SecureXmlFactory() {
    }

    public static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        setIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new javax.xml.stream.XMLStreamException("External resources are not allowed: " + systemId);
        });
        return factory;
    }

    private static void setIfSupported(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException ignored) {
            // property unknown to this StAX implementation; the other switches already block access
        }
    }
}
