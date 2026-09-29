package gov.openpto.ingest.transform.model;

/** A cited patent; {@code citedBy} is {@code EXAMINER} or {@code APPLICANT}. */
public record Citation(String patentNumber, String citedBy) {
}