package gov.openpto.ingest.service;

import static gov.openpto.ingest.TestFixtures.ADMIN;
import static gov.openpto.ingest.TestFixtures.ALICE;
import static gov.openpto.ingest.TestFixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.dto.PageResponse;
import gov.openpto.ingest.dto.StatsResponse;
import gov.openpto.ingest.exception.BadRequestException;
import gov.openpto.ingest.exception.ConflictException;
import gov.openpto.ingest.exception.ForbiddenException;
import gov.openpto.ingest.exception.NotFoundException;
import gov.openpto.ingest.model.IngestJob;
import gov.openpto.ingest.model.IngestJobError;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestJobServiceTest {

    @Mock
    IngestJobRepository jobs;
    @Mock
    IngestJobStageRepository stages;
    @Mock
    IngestJobErrorRepository errors;
    @Mock
    ObjectStorage storage;
    @Mock
    ApplicationEventPublisher publisher;

    private IngestJobService service;
    private IngestJob alicesJob;

    @BeforeEach
    void setUp() {
        service = new IngestJobService(jobs, stages, errors, storage, publisher, TestFixtures.properties(Path.of("x")));
        alicesJob = TestFixtures.job(ALICE, JobStatus.FAILED);
        when(jobs.findById(alicesJob.getId())).thenReturn(Optional.of(alicesJob));
        when(jobs.save(any(IngestJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stages.save(any(IngestJobStage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Page<IngestJob> page(IngestJob... content) {
        return new PageImpl<>(List.of(content), PageRequest.of(0, 20), content.length);
    }

    @Test
    void list_user_seesOnlyOwnJobs() {
        when(jobs.findByOwnerId(any(), any())).thenReturn(page(alicesJob));

        PageResponse<IngestJobResponse> result = service.list(ALICE, null, 0, 20, null);

        assertThat(result.content()).extracting(IngestJobResponse::id).containsExactly(alicesJob.getId());
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).findByOwnerId(org.mockito.ArgumentMatchers.eq(ALICE.id()), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        verify(jobs, never()).findAll(any(Pageable.class));
    }

    @Test
    void list_statusFilterAndAdminVariants() {
        when(jobs.findByOwnerIdAndStatus(any(), any(), any())).thenReturn(page());
        when(jobs.findAll(any(Pageable.class))).thenReturn(page(alicesJob));
        when(jobs.findByStatus(any(), any())).thenReturn(page());

        service.list(ALICE, JobStatus.FAILED, 0, 500, "fileName,asc");
        assertThat(service.list(ADMIN, null, -1, 0, "status").content()).hasSize(1);
        service.list(ADMIN, JobStatus.COMPLETED, 2, 10, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).findByOwnerIdAndStatus(org.mockito.ArgumentMatchers.eq(ALICE.id()),
                org.mockito.ArgumentMatchers.eq(JobStatus.FAILED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("fileName").getDirection()).isEqualTo(Sort.Direction.ASC);
        verify(jobs).findByStatus(org.mockito.ArgumentMatchers.eq(JobStatus.COMPLETED), any());
    }

    @Test
    void list_unknownSortField_isBadRequest() {
        assertThatThrownBy(() -> service.list(ALICE, null, 0, 20, "ownerId,asc"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unsupported sort field");
    }

    @Test
    void get_owner_seesStagesAndErrors() {
        alicesJob.setStartedAt(Instant.parse("2025-01-07T10:00:00Z"));
        alicesJob.setFinishedAt(Instant.parse("2025-01-07T10:00:01.500Z"));
        when(stages.findByJobIdOrderByAtAscIdAsc(alicesJob.getId())).thenReturn(List.of(
                new IngestJobStage(alicesJob.getId(), StageName.UPLOADED, StageStatus.COMPLETED, "stored")));
        when(errors.findByJobIdOrderByRecordIndexAscIdAsc(alicesJob.getId(), Limit.of(200))).thenReturn(List.of(
                new IngestJobError(alicesJob.getId(), 1, "US1", "bad", gov.openpto.ingest.model.ErrorPhase.TRANSFORM)));

        IngestJobResponse response = service.get(alicesJob.getId(), ALICE);

        assertThat(response.stages()).extracting(IngestJobResponse.StageResponse::stage).containsExactly("UPLOADED");
        assertThat(response.errors()).extracting(IngestJobResponse.RecordErrorResponse::identifier).containsExactly("US1");
        assertThat(response.durationMs()).isEqualTo(1500);
        assertThat(response.ownerId()).isEqualTo(ALICE.id());
    }

    @Test
    void get_otherUser_isForbidden_adminAllowed_unknownNotFound() {
        assertThatThrownBy(() -> service.get(alicesJob.getId(), BOB)).isInstanceOf(ForbiddenException.class);
        assertThat(service.get(alicesJob.getId(), ADMIN).id()).isEqualTo(alicesJob.getId());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), ALICE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void retry_failedJob_resetsAndRepublishesObjectCreated() throws IOException {
        alicesJob.setRecordsTotal(5);
        alicesJob.setRecordsFailed(5);
        ObjectMetadata raw = new ObjectMetadata("openpto-raw", alicesJob.getRawObjectKey(), 1234, "etag", Instant.now());
        when(storage.head("openpto-raw", alicesJob.getRawObjectKey())).thenReturn(Optional.of(raw));

        IngestJobResponse response = service.retry(alicesJob.getId(), ALICE);

        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(alicesJob.getRecordsTotal()).isZero();
        assertThat(alicesJob.getRecordsFailed()).isZero();
        verify(stages).deleteByJobId(alicesJob.getId());
        verify(errors).deleteByJobId(alicesJob.getId());
        verify(publisher).publishEvent(ObjectCreatedEvent.of(raw));
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"QUEUED", "PARSING", "TRANSFORMED", "LOADING", "COMPLETED"})
    void retry_nonRetryableStatus_isConflict(JobStatus status) {
        alicesJob.setStatus(status);

        assertThatThrownBy(() -> service.retry(alicesJob.getId(), ALICE)).isInstanceOf(ConflictException.class);
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void retry_partialAllowed_butRawObjectMissingIsConflict() throws IOException {
        alicesJob.setStatus(JobStatus.PARTIAL);
        when(storage.head(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(alicesJob.getId(), ALICE))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no longer exists");
    }

    @Test
    void retry_otherUser_isForbidden() {
        assertThatThrownBy(() -> service.retry(alicesJob.getId(), BOB)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_owner_removesRowsAndObjects() throws IOException {
        alicesJob.setJsonBucket("openpto-processed");
        alicesJob.setJsonObjectKey(alicesJob.getRawObjectKey() + ".json");
        when(storage.delete("openpto-processed", alicesJob.getJsonObjectKey())).thenThrow(new IOException("locked"));

        service.delete(alicesJob.getId(), ALICE);

        verify(jobs).delete(alicesJob);
        verify(storage).delete("openpto-raw", alicesJob.getRawObjectKey());
        verify(storage).delete("openpto-processed", alicesJob.getJsonObjectKey());
    }

    @Test
    void delete_otherUser_isForbidden_adminAllowed() throws IOException {
        assertThatThrownBy(() -> service.delete(alicesJob.getId(), BOB)).isInstanceOf(ForbiddenException.class);
        verify(jobs, never()).delete(any());

        service.delete(alicesJob.getId(), ADMIN);
        verify(jobs).delete(alicesJob);
    }

    @Test
    void jsonDownload_streamsProcessedObject() throws IOException {
        alicesJob.setJsonBucket("openpto-processed");
        alicesJob.setJsonObjectKey("jobs/x/ipg250107-sample.xml.json");
        when(storage.head("openpto-processed", "jobs/x/ipg250107-sample.xml.json"))
                .thenReturn(Optional.of(new ObjectMetadata("openpto-processed", "k", 2, "e", Instant.now())));
        when(storage.stream(any(), any(), any())).thenAnswer(inv -> {
            inv.<java.io.OutputStream>getArgument(2).write("[]".getBytes());
            return 2L;
        });

        JsonDownload download = service.jsonDownload(alicesJob.getId(), ALICE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download.writer().writeTo(out);

        assertThat(download.fileName()).isEqualTo("ipg250107-sample.json");
        assertThat(download.sizeBytes()).isEqualTo(2);
        assertThat(out.toString()).isEqualTo("[]");
    }

    @Test
    void jsonDownload_notTransformedYetOrObjectGone_isNotFound() throws IOException {
        assertThatThrownBy(() -> service.jsonDownload(alicesJob.getId(), ALICE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("no transformed JSON");

        alicesJob.setJsonBucket("openpto-processed");
        alicesJob.setJsonObjectKey("gone.json");
        when(storage.head(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.jsonDownload(alicesJob.getId(), ALICE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("no longer available");
    }

    @Test
    void stats_userAndAdmin() {
        IngestJobRepository.StatusCount completed = new IngestJobRepository.StatusCount() {
            public JobStatus getValue() {
                return JobStatus.COMPLETED;
            }

            public long getCount() {
                return 3;
            }
        };
        when(jobs.countByStatusForOwner(ALICE.id())).thenReturn(List.of(completed));
        when(jobs.sumRecordsLoadedForOwner(ALICE.id())).thenReturn(15L);
        when(jobs.lastCreatedAtForOwner(ALICE.id())).thenReturn(Instant.parse("2025-01-07T10:00:00Z"));
        when(jobs.countByStatus()).thenReturn(List.of(completed, completed));
        when(jobs.sumRecordsLoaded()).thenReturn(30L);

        StatsResponse mine = service.stats(ALICE);
        StatsResponse all = service.stats(ADMIN);

        assertThat(mine.jobs()).isEqualTo(3);
        assertThat(mine.byStatus()).containsExactly(new StatsResponse.ValueCount("COMPLETED", 3));
        assertThat(mine.recordsLoaded()).isEqualTo(15);
        assertThat(mine.lastJobAt()).isEqualTo(Instant.parse("2025-01-07T10:00:00Z"));
        assertThat(all.jobs()).isEqualTo(6);
        assertThat(all.recordsLoaded()).isEqualTo(30);
    }
}