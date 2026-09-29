package gov.openpto.ingest.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed {@code app.*} configuration (defaults live in application.yml, overridable by env). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String jwksUri, String jwtIssuer, Storage storage, Odp odp, Ingest ingest) {

    /** Local S3 emulation: each bucket is a folder under {@code root}. */
    public record Storage(String root, String rawBucket, String processedBucket) {
    }

    /** odp-service internal bulk-upsert client. */
    public record Odp(String baseUrl, String internalToken, Duration connectTimeout, Duration readTimeout,
                      int maxAttempts, Duration initialBackoff, int batchSize) {
    }

    /** Upload limits and the async pipeline executor. */
    public record Ingest(int maxFiles, long maxFileBytes, int maxZipEntries, long maxZipTotalBytes,
                         int maxCompressionRatio, int corePoolSize, int maxPoolSize, int queueCapacity,
                         Duration shutdownWait, Duration stuckAfter, int maxStoredErrors) {
    }
}