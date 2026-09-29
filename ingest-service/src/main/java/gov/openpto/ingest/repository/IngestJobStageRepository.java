package gov.openpto.ingest.repository;

import gov.openpto.ingest.model.IngestJobStage;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestJobStageRepository extends JpaRepository<IngestJobStage, Long> {

    List<IngestJobStage> findByJobIdOrderByAtAscIdAsc(java.util.UUID jobId);

    @Modifying
    @Query("delete from IngestJobStage s where s.jobId = :jobId")
    int deleteByJobId(@Param("jobId") UUID jobId);
}