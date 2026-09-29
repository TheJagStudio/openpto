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
@Table(name = "ingest_job_stage")
@Getter
@Setter
@NoArgsConstructor
public class IngestJobStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageName stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageStatus status;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false)
    private Instant at;

    public IngestJobStage(UUID jobId, StageName stage, StageStatus status, String message) {
        this.jobId = jobId;
        this.stage = stage;
        this.status = status;
        this.message = IngestJob.truncate(message, 2000);
        this.at = Instant.now();
    }
}