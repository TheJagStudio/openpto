package gov.openpto.ingest.repository;

import gov.openpto.ingest.model.IngestJobError;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestJobErrorRepository extends JpaRepository<IngestJobError, Long> {

    List<IngestJobError> findByJobIdOrderByRecordIndexAscIdAsc(UUID jobId, Limit limit);

    long countByJobId(UUID jobId);

    @Modifying
    @Query("delete from IngestJobError e where e.jobId = :jobId")
    int deleteByJobId(@Param("jobId") UUID jobId);
}