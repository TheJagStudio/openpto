package gov.openpto.ingest.repository;

import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.JobStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestJobRepository extends JpaRepository<IngestJob, UUID> {

    Page<IngestJob> findByOwnerId(String ownerId, Pageable pageable);

    Page<IngestJob> findByOwnerIdAndStatus(String ownerId, JobStatus status, Pageable pageable);

    Page<IngestJob> findByStatus(JobStatus status, Pageable pageable);

    List<IngestJob> findByStatusInAndUpdatedAtBefore(Collection<JobStatus> statuses, Instant before);

    /** Projection for the stats endpoint. */
    interface StatusCount {
        JobStatus getValue();

        long getCount();
    }

    @Query("select j.status as value, count(j) as count from IngestJob j group by j.status order by j.status")
    List<StatusCount> countByStatus();

    @Query("select j.status as value, count(j) as count from IngestJob j where j.ownerId = :ownerId "
            + "group by j.status order by j.status")
    List<StatusCount> countByStatusForOwner(@Param("ownerId") String ownerId);

    @Query("select coalesce(sum(j.recordsLoaded), 0) from IngestJob j")
    long sumRecordsLoaded();

    @Query("select coalesce(sum(j.recordsLoaded), 0) from IngestJob j where j.ownerId = :ownerId")
    long sumRecordsLoadedForOwner(@Param("ownerId") String ownerId);

    @Query("select max(j.createdAt) from IngestJob j")
    Instant lastCreatedAt();

    @Query("select max(j.createdAt) from IngestJob j where j.ownerId = :ownerId")
    Instant lastCreatedAtForOwner(@Param("ownerId") String ownerId);

    long countByOwnerId(String ownerId);
}