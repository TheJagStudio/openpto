package gov.openpto.ingest.model;

import gov.openpto.ingest.transform.DocumentFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ingest_job")
@Getter
@Setter
public class IngestJob {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_format", nullable = false, length = 30)
    private DocumentFormat documentFormat = DocumentFormat.UNKNOWN;

    @Column(name = "records_total", nullable = false)
    private int recordsTotal;

    @Column(name = "records_loaded", nullable = false)
    private int recordsLoaded;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    @Column(name = "raw_bucket", nullable = false, length = 100)
    private String rawBucket;

    @Column(name = "raw_object_key", nullable = false, length = 1024)
    private String rawObjectKey;

    @Column(name = "raw_etag", length = 100)
    private String rawEtag;

    @Column(name = "json_bucket", length = 100)
    private String jsonBucket;

    @Column(name = "json_object_key", length = 1024)
    private String jsonObjectKey;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Stores a message truncated to the column size. */
    public void setMessage(String message) {
        this.message = truncate(message, 2000);
    }

    public static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }
}