package gov.openpto.ingest.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.config.SecurityConfig;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.dto.PageResponse;
import gov.openpto.ingest.dto.StatsResponse;
import gov.openpto.ingest.exception.ConflictException;
import gov.openpto.ingest.exception.ForbiddenException;
import gov.openpto.ingest.exception.InvalidUploadException;
import gov.openpto.ingest.exception.NotFoundException;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.service.IngestJobService;
import gov.openpto.ingest.service.JobMapper;
import gov.openpto.ingest.service.JsonDownload;
import gov.openpto.ingest.service.UploadService;
import gov.openpto.ingest.web.CurrentUser;
import gov.openpto.ingest.web.ProblemResponseWriter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(IngestController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class})
class IngestControllerTest {

    private static final String ALICE = TestFixtures.ALICE.id();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UploadService uploadService;
    @MockitoBean
    IngestJobService jobService;

    static JwtRequestPostProcessor user(String sub, String... roles) {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt().jwt(j -> j.subject(sub).claim("roles", List.of(roles))).authorities(authorities);
    }

    private static IngestJobResponse response(JobStatus status) {
        var job = TestFixtures.job(TestFixtures.ALICE, status);
        return JobMapper.toResponse(job, List.of(), List.of());
    }

    @Test
    void upload_withoutJwt_is401ProblemWithRequestId() throws Exception {
        mvc.perform(multipart("/api/v1/ingest/uploads").file(new MockMultipartFile("files", "a.xml", "application/xml",
                        "<a/>".getBytes())).header("X-Request-Id", "req-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-123"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").value("req-123"));
        verifyNoInteractions(uploadService);
    }

    @Test
    void jobs_tokenWithoutUserRole_is403() throws Exception {
        mvc.perform(get("/api/v1/ingest/jobs").with(user(ALICE, "GUEST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void upload_validXml_is202WithJobs() throws Exception {
        when(uploadService.upload(anyList(), any())).thenReturn(List.of(response(JobStatus.QUEUED)));

        mvc.perform(multipart("/api/v1/ingest/uploads")
                        .file(new MockMultipartFile("files", "a.xml", "application/xml", "<a/>".getBytes()))
                        .with(user(ALICE, "USER")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Request-Id", notNullValue()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andExpect(jsonPath("$[0].documentFormat").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].createdAt").value("2025-01-07T10:00:00Z"));
        verify(uploadService).upload(anyList(), eq(new CurrentUser(ALICE, false)));
    }

    @Test
    void upload_missingFilesPart_is400() throws Exception {
        mvc.perform(multipart("/api/v1/ingest/uploads")
                        .file(new MockMultipartFile("other", "a.xml", "application/xml", "<a/>".getBytes()))
                        .with(user(ALICE, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void upload_rejectedContent_is400WithFieldErrors() throws Exception {
        when(uploadService.upload(anyList(), any()))
                .thenThrow(new InvalidUploadException("files", "a.pdf: only .xml or .zip files are accepted"));

        mvc.perform(multipart("/api/v1/ingest/uploads")
                        .file(new MockMultipartFile("files", "a.pdf", "application/pdf", "%PDF".getBytes()))
                        .with(user(ALICE, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid upload"))
                .andExpect(jsonPath("$.errors[0].field").value("files"))
                .andExpect(jsonPath("$.instance").value("/api/v1/ingest/uploads"));
    }

    @Test
    void upload_notMultipart_is415() throws Exception {
        mvc.perform(post("/api/v1/ingest/uploads").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user(ALICE, "USER")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void list_passesPagingAndValidatesSize() throws Exception {
        when(jobService.list(any(), isNull(), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageResponse<>(List.of(response(JobStatus.COMPLETED)), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/ingest/jobs").with(user(ALICE, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mvc.perform(get("/api/v1/ingest/jobs").param("size", "101").with(user(ALICE, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/v1/ingest/jobs").param("status", "NOPE").with(user(ALICE, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void get_otherOwner_is403_unknown_is404_badUuid_is400() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.get(eq(id), any())).thenThrow(new ForbiddenException("belongs to another user"));
        UUID missing = UUID.randomUUID();
        when(jobService.get(eq(missing), any())).thenThrow(new NotFoundException("Ingest job not found"));

        mvc.perform(get("/api/v1/ingest/jobs/{id}", id).with(user(TestFixtures.BOB.id(), "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("belongs to another user"));
        mvc.perform(get("/api/v1/ingest/jobs/{id}", missing).with(user(ALICE, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://openpto.local/problems/not-found"));
        mvc.perform(get("/api/v1/ingest/jobs/not-a-uuid").with(user(ALICE, "USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_admin_isPassedAsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.get(eq(id), any())).thenReturn(response(JobStatus.COMPLETED));

        mvc.perform(get("/api/v1/ingest/jobs/{id}", id).with(user("admin-1", "USER", "ADMIN")))
                .andExpect(status().isOk());
        verify(jobService).get(id, new CurrentUser("admin-1", true));
    }

    @Test
    void json_isStreamedAsAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] body = "[\n{\"patentNumber\":\"US1B2\"}\n]\n".getBytes(StandardCharsets.UTF_8);
        when(jobService.jsonDownload(eq(id), any())).thenReturn(new JsonDownload("grant.json", body.length,
                out -> out.write(body)));

        MvcResult started = mvc.perform(get("/api/v1/ingest/jobs/{id}/json", id).with(user(ALICE, "USER")))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"grant.json\""))
                .andExpect(jsonPath("$[0].patentNumber").value("US1B2"));
    }

    @Test
    void retry_conflict_is409_andAccepted_is202() throws Exception {
        UUID conflict = UUID.randomUUID();
        when(jobService.retry(eq(conflict), any())).thenThrow(new ConflictException("Only FAILED or PARTIAL"));
        UUID ok = UUID.randomUUID();
        when(jobService.retry(eq(ok), any())).thenReturn(response(JobStatus.QUEUED));

        mvc.perform(post("/api/v1/ingest/jobs/{id}/retry", conflict).with(user(ALICE, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
        mvc.perform(post("/api/v1/ingest/jobs/{id}/retry", ok).with(user(ALICE, "USER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void delete_is204_orForbidden() throws Exception {
        UUID ok = UUID.randomUUID();
        UUID others = UUID.randomUUID();
        doThrow(new ForbiddenException("no")).when(jobService).delete(eq(others), any());

        mvc.perform(delete("/api/v1/ingest/jobs/{id}", ok).with(user(ALICE, "USER"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/ingest/jobs/{id}", others).with(user(ALICE, "USER"))).andExpect(status().isForbidden());
    }

    @Test
    void stats_returnsCounts() throws Exception {
        when(jobService.stats(any())).thenReturn(new StatsResponse(2,
                List.of(new StatsResponse.ValueCount("COMPLETED", 2)), 10, Instant.parse("2025-01-07T10:00:00Z")));

        mvc.perform(get("/api/v1/ingest/stats").with(user(ALICE, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs").value(2))
                .andExpect(jsonPath("$.byStatus[0].value").value("COMPLETED"))
                .andExpect(jsonPath("$.lastJobAt").value("2025-01-07T10:00:00Z"));
    }

    @Test
    void unexpectedError_is500WithoutStackTrace() throws Exception {
        when(jobService.stats(any())).thenThrow(new IllegalStateException("secret internals"));

        mvc.perform(get("/api/v1/ingest/stats").with(user(ALICE, "USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}