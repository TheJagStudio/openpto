package gov.openpto.gateway.usage;

import java.util.List;

/** Port to odp-service {@code POST /internal/v1/usage}. */
public interface UsageClient {

    /**
     * Sends one batch. Throws on any failure so the caller can keep the counts.
     */
    void send(List<UsageEntry> entries);
}
