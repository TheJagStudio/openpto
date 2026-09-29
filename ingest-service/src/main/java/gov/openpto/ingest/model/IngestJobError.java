package gov.openpto.ingest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ingest_job_error")
@Getter
@Setter
@NoArgsConstructor
public class IngestJobError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "record_index", nullable = false)
    private int recordIndex;

    @Column(length = 200)
    private String identifier;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ErrorPhase phase;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IngestJobError(UUID jobId, int recordIndex, String identifier, String message, ErrorPhase phase) {
        this.jobId = jobId;
        this.recordIndex = recordIndex;
        this.identifier = IngestJob.truncate(identifier, 200);
        this.message = IngestJob.truncate(message == null ? "Unknown error" : message, 2000);
        this.phase = phase;
        this.createdAt = Instant.now();
    }
}