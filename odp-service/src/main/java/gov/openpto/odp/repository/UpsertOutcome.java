package gov.openpto.odp.repository;

/** Result of a single-row upsert: the row id and whether it was newly inserted. */
public record UpsertOutcome(long id, boolean inserted) {

}
