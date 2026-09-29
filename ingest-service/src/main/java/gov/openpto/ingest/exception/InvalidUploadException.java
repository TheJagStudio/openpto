package gov.openpto.ingest.exception;

/** The uploaded content was rejected (type, size, sniffed content, unsafe archive). */
public class InvalidUploadException extends BadRequestException {

    public InvalidUploadException(String field, String detail) {
        super("Invalid upload", field, detail);
    }
}