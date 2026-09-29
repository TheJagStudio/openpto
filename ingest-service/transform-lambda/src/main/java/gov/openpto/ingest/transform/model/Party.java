package gov.openpto.ingest.transform.model;

/** Inventor or assignee. For organisations {@code name} is the organisation name. */
public record Party(String name, String city, String state, String country) {
}