package gov.openpto.ingest.transform.mapper;

import gov.openpto.ingest.transform.DocumentFormat;
import gov.openpto.ingest.transform.xml.XmlNode;

/** Maps one record element (a patent document root or a trademark {@code case-file}) to a neutral record. */
public interface RecordMapper {

    /**
     * @throws gov.openpto.ingest.transform.MappingException when a required field is missing
     */
    Object map(XmlNode node, String ingestJobId);

    /** Best-effort identifier (patent / serial number) used in error reports; may be {@code null}. */
    String identify(XmlNode node);

    static RecordMapper forFormat(DocumentFormat format) {
        return switch (format) {
            case US_PATENT_GRANT -> new IcePatentMapper(true);
            case US_PATENT_APPLICATION -> new IcePatentMapper(false);
            case PATDOC_LEGACY -> new PatdocMapper();
            case TRADEMARK_DAILY -> new TrademarkCaseFileMapper();
            case UNKNOWN -> throw new IllegalArgumentException("No mapper for UNKNOWN format");
        };
    }
}
