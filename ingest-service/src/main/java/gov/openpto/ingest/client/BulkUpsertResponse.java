package gov.openpto.ingest.client;

import java.util.List;

/** odp-service bulk-upsert response: {@code { inserted, updated, failed:[{ id, error }] }}. */
public record BulkUpsertResponse(int inserted, int updated, List<Failure> failed) {

    public BulkUpsertResponse {
        failed = failed == null ? List.of() : failed;
    }

    public int loaded() {
        return inserted + updated;
    }

    public record Failure(String id, String error) {
    }
}