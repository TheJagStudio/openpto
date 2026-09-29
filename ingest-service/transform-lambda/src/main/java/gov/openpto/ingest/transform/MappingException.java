package gov.openpto.ingest.transform;

/** A document was well-formed XML but could not be mapped (e.g. a required field is missing). */
public class MappingException extends RuntimeException {

    private final String identifier;

    public MappingException(String identifier, String message) {
        super(message);
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }
}