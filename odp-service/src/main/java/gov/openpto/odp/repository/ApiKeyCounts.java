package gov.openpto.odp.repository;

import java.util.UUID;

/** Per-user key counts for the admin users page. */
public record ApiKeyCounts(UUID userId, long total, long active) {

}
