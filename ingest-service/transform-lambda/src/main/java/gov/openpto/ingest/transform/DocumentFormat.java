package gov.openpto.ingest.transform;

import java.util.Locale;

/** Supported USPTO bulk formats, detected from the root element of each document. */
public enum DocumentFormat {
    US_PATENT_GRANT(Target.PATENTS),
    US_PATENT_APPLICATION(Target.PATENTS),
    PATDOC_LEGACY(Target.PATENTS),
    TRADEMARK_DAILY(Target.TRADEMARKS),
    UNKNOWN(null);

    /** Which odp bulk-upsert endpoint the records of a format are loaded into. */
    public enum Target {PATENTS, TRADEMARKS}

    private final Target target;

    DocumentFormat(Target target) {
        this.target = target;
    }

    public Target target() {
        return target;
    }

    public static DocumentFormat fromRootElement(String rootElement) {
        if (rootElement == null) {
            return UNKNOWN;
        }
        return switch (rootElement.toLowerCase(Locale.ROOT)) {
            case "us-patent-grant" -> US_PATENT_GRANT;
            case "us-patent-application", "us-patent-application-publication" -> US_PATENT_APPLICATION;
            case "patdoc" -> PATDOC_LEGACY;
            case "trademark-applications-daily" -> TRADEMARK_DAILY;
            default -> UNKNOWN;
        };
    }
}