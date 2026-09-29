package gov.openpto.ingest.transform;

/**
 * A record that could not be transformed. {@code index} is the 0-based position of the record in the source
 * file; {@code identifier} is the patent/serial number when it could be determined.
 */
public record RecordError(int index, String identifier, String message) {
}