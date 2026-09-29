package gov.openpto.ingest;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.web.CurrentUser;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Shared test data. */
public final class TestFixtures {

    public static final CurrentUser ALICE = new CurrentUser("11111111-1111-1111-1111-111111111111", false);
    public static final CurrentUser BOB = new CurrentUser("22222222-2222-2222-2222-222222222222", false);
    public static final CurrentUser ADMIN = new CurrentUser("99999999-9999-9999-9999-999999999999", true);

    private TestFixtures() {
    }

    public static AppProperties properties(Path storageRoot) {
        return new AppProperties("http://localhost:8081/.well-known/jwks.json", "openpto",
                new AppProperties.Storage(storageRoot.toString(), "openpto-raw", "openpto-processed"),
                new AppProperties.Odp("http://odp", "test-token", Duration.ofSeconds(1), Duration.ofSeconds(5), 3,
                        Duration.ofMillis(100), 500),
                new AppProperties.Ingest(10, 50L * 1024 * 1024, 20, 100L * 1024 * 1024, 100, 1, 1, 10,
                        Duration.ofSeconds(1), Duration.ofMinutes(10), 1000));
    }

    public static IngestJob job(CurrentUser owner, JobStatus status) {
        IngestJob job = new IngestJob();
        UUID id = UUID.randomUUID();
        job.setId(id);
        job.setOwnerId(owner.id());
        job.setFileName("ipg250107-sample.xml");
        job.setSizeBytes(1234);
        job.setStatus(status);
        job.setRawBucket("openpto-raw");
        job.setRawObjectKey("jobs/" + id + "/ipg250107-sample.xml");
        job.setCreatedAt(Instant.parse("2025-01-07T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2025-01-07T10:00:00Z"));
        return job;
    }
}