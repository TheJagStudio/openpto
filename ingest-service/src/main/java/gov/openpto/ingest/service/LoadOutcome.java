package gov.openpto.ingest.service;

import gov.openpto.ingest.transform.RecordError;

import java.util.List;

/** Result of loading a processed JSON object into odp-service. */
public record LoadOutcome(int loaded, int failed, List<RecordError> errors) {
}