package gov.openpto.ingest.service;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.dto.PageResponse;
import gov.openpto.ingest.dto.StatsResponse;
import gov.openpto.ingest.exception.ConflictException;
import gov.openpto.ingest.exception.ForbiddenException;
import gov.openpto.ingest.exception.BadRequestException;
import gov.openpto.ingest.exception.NotFoundException;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobStage;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.model.StageName;
import gov.openpto.ingest.model.StageStatus;
import gov.openpto.ingest.repository.IngestJobErrorRepository;
import gov.openpto.ingest.repository.IngestJobRepository;
import gov.openpto.ingest.repository.IngestJobStageRepository;
import gov.openpto.ingest.storage.ObjectCreatedEvent;
import gov.openpto.ingest.storage.ObjectMetadata;
import gov.openpto.ingest.storage.ObjectStorage;
import gov.openpto.ingest.web.CurrentUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job queries and commands with ownership rules: users see their own jobs, ADMIN sees all. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class IngestJobService {

    public static final int DETAIL_ERROR_LIMIT = 200;
    public static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt", "fileName", "fileName", "status", "status", "sizeBytes", "sizeBytes",
            "finishedAt", "finishedAt", "recordsTotal", "recordsTotal");

    private final IngestJobRepository jobs;
    private final IngestJobStageRepository stages;
    private final IngestJobErrorRepository errors;
    private final ObjectStorage storage;
    private final ApplicationEventPublisher publisher;

    public IngestJobService(IngestJobRepository jobs, IngestJobStageRepository stages, IngestJobErrorRepository errors,
                            ObjectStorage storage, ApplicationEventPublisher publisher, AppProperties properties) {
        this.jobs = jobs;
        this.stages = stages;
        this.errors = errors;
        this.storage = storage;
        this.publisher = publisher;
    }

    public PageResponse<IngestJobResponse> list(CurrentUser user, JobStatus status, int page, int size, String sort) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), parseSort(sort));
        Page<IngestJob> result;
        if (user.admin()) {
            result = status == null ? jobs.findAll(pageable) : jobs.findByStatus(status, pageable);
        } else {
            result = status == null ? jobs.findByOwnerId(user.id(), pageable)
                    : jobs.findByOwnerIdAndStatus(user.id(), status, pageable);
        }
        return PageResponse.of(result.map(job -> JobMapper.toResponse(job, List.of(), List.of())));
    }

    public IngestJobResponse get(UUID id, CurrentUser user) {
        IngestJob job = accessible(id, user);
        return detail(job);
    }

    public JsonDownload jsonDownload(UUID id, CurrentUser user) {
        IngestJob job = accessible(id, user);
        if (job.getJsonObjectKey() == null) {
            throw new NotFoundException("Job " + id + " has no transformed JSON yet (status " + job.getStatus() + ")");
        }
        String bucket = job.getJsonBucket();
        String key = job.getJsonObjectKey();
        ObjectMetadata metadata;
        try {
            metadata = storage.head(bucket, key)
                    .orElseThrow(() -> new NotFoundException("Transformed JSON for job " + id + " is no longer available"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String fileName = UploadService.safeKeyName(job.getFileName()).replaceAll("(?i)\\.xml$", "") + ".json";
        return new JsonDownload(fileName, metadata.size(), out -> storage.stream(bucket, key, out));
    }

    @Transactional
    public IngestJobResponse retry(UUID id, CurrentUser user) {
        IngestJob job = accessible(id, user);
        if (!job.getStatus().isRetryable()) {
            throw new ConflictException("Only FAILED or PARTIAL jobs can be retried; job is " + job.getStatus());
        }
        ObjectMetadata raw;
        try {
            raw = storage.head(job.getRawBucket(), job.getRawObjectKey())
                    .orElseThrow(() -> new ConflictException("The raw object of job " + id + " no longer exists"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        stages.deleteByJobId(id);
        errors.deleteByJobId(id);
        job.setStatus(JobStatus.QUEUED);
        job.setRecordsTotal(0);
        job.setRecordsLoaded(0);
        job.setRecordsFailed(0);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setMessage("Retry requested");
        job = jobs.save(job);
        IngestJobStage uploaded = stages.save(new IngestJobStage(id, StageName.UPLOADED, StageStatus.COMPLETED,
                "Retry " + (job.getAttempts() + 1) + " from s3://" + raw.bucket() + "/" + raw.key()));
        // re-deliver the S3 notification; the pipeline picks it up after this transaction commits
        publisher.publishEvent(ObjectCreatedEvent.of(raw));
        log.info("Job {} re-queued by {}", id, user.id());
        return JobMapper.toResponse(job, List.of(uploaded), List.of());
    }

    @Transactional
    public void delete(UUID id, CurrentUser user) {
        IngestJob job = accessible(id, user);
        stages.deleteByJobId(id);
        errors.deleteByJobId(id);
        jobs.delete(job);
        deleteObject(job.getRawBucket(), job.getRawObjectKey());
        if (job.getJsonObjectKey() != null) {
            deleteObject(job.getJsonBucket(), job.getJsonObjectKey());
        }
        log.info("Job {} deleted by {}", id, user.id());
    }

    public StatsResponse stats(CurrentUser user) {
        List<IngestJobRepository.StatusCount> counts = user.admin() ? jobs.countByStatus()
                : jobs.countByStatusForOwner(user.id());
        List<StatsResponse.ValueCount> byStatus = counts.stream()
                .map(c -> new StatsResponse.ValueCount(c.getValue().name(), c.getCount()))
                .toList();
        long total = byStatus.stream().mapToLong(StatsResponse.ValueCount::count).sum();
        return new StatsResponse(total, byStatus,
                user.admin() ? jobs.sumRecordsLoaded() : jobs.sumRecordsLoadedForOwner(user.id()),
                user.admin() ? jobs.lastCreatedAt() : jobs.lastCreatedAtForOwner(user.id()));
    }

    private IngestJobResponse detail(IngestJob job) {
        return JobMapper.toResponse(job, stages.findByJobIdOrderByAtAscIdAsc(job.getId()),
                errors.findByJobIdOrderByRecordIndexAscIdAsc(job.getId(), Limit.of(DETAIL_ERROR_LIMIT)));
    }

    private IngestJob accessible(UUID id, CurrentUser user) {
        IngestJob job = jobs.findById(id).orElseThrow(() -> new NotFoundException("Ingest job " + id + " not found"));
        if (!user.canAccess(job.getOwnerId())) {
            throw new ForbiddenException("Ingest job " + id + " belongs to another user");
        }
        return job;
    }

    private void deleteObject(String bucket, String key) {
        if (bucket == null || key == null) {
            return;
        }
        try {
            storage.delete(bucket, key);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete s3://{}/{}: {}", bucket, key, e.getMessage());
        }
    }

    static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = SORTABLE.get(parts[0].trim());
        if (property == null) {
            throw new BadRequestException("sort", "Unsupported sort field '" + parts[0].trim() + "'; use one of "
                    + SORTABLE.keySet().stream().sorted().toList());
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}