package gov.openpto.ingest.model;

/** Where a record error happened: while transforming XML, or while loading into odp-service. */
public enum ErrorPhase {
    TRANSFORM, LOAD
}