package gov.openpto.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.ingest.client.BulkUpsertResponse;
import gov.openpto.ingest.client.OdpClient;
import gov.openpto.ingest.client.OdpClientException;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.repository.IngestJobRepository;
import gov.openpto.ingest.transform.DocumentFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full pipeline on the real database (openpto_test, schema ingest) and local buckets:
 * upload → raw bucket → ObjectCreated event → transform-lambda → processed bucket → (mocked) odp bulk-upsert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngestIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    IngestJobRepository jobs;

    @MockitoBean
    OdpClient odpClient;

    private final String owner = UUID.randomUUID().toString();

    private JwtRequestPostProcessor asOwner() {
        return jwt().jwt(j -> j.subject(owner).claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private JsonNode upload(String sample) throws Exception {
        byte[] content = Files.readAllBytes(Path.of("samples", sample));
        MvcResult result = mvc.perform(multipart("/api/v1/ingest/uploads")
                        .file(new MockMultipartFile("files", sample, "application/xml", content))
                        .with(asOwner()))
                .andExpect(status().isAccepted())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get(0);
    }

    private JsonNode awaitStatus(String id, String... terminal) {
        JsonNode[] last = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            MvcResult r = mvc.perform(get("/api/v1/ingest/jobs/{id}", id).with(asOwner())).andReturn();
            last[0] = json.readTree(r.getResponse().getContentAsString());
            return List.of(terminal).contains(last[0].get("status").asString());
        });
        return last[0];
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadGrantSample_isTransformedLoadedAndDownloadable() throws Exception {
        when(odpClient.bulkUpsert(eq(DocumentFormat.Target.PATENTS), anyList()))
                .thenAnswer(inv -> new BulkUpsertResponse(((List<JsonNode>) inv.getArgument(1)).size(), 0, List.of()));

        JsonNode queued = upload("ipg250107-sample.xml");
        assertThat(queued.get("status").asString()).isEqualTo("QUEUED");
        String id = queued.get("id").asString();

        JsonNode job = awaitStatus(id, "COMPLETED", "PARTIAL", "FAILED");
        assertThat(job.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(job.get("documentFormat").asString()).isEqualTo("US_PATENT_GRANT");
        assertThat(job.get("recordsTotal").asInt()).isEqualTo(5);
        assertThat(job.get("recordsLoaded").asInt()).isEqualTo(5);
        assertThat(job.get("stages")).extracting(s -> s.get("stage").asString())
                .containsExactly("UPLOADED", "PARSED", "TRANSFORMED", "LOADED");
        assertThat(job.get("durationMs").asLong()).isGreaterThanOrEqualTo(0);

        MvcResult started = mvc.perform(get("/api/v1/ingest/jobs/{id}/json", id).with(asOwner())).andReturn();
        MvcResult download = mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();
        JsonNode records = json.readTree(download.getResponse().getContentAsByteArray());
        assertThat(records.size()).isEqualTo(5);
        assertThat(records.get(0).get("patentNumber").asString()).isEqualTo("US12345601B2");
        assertThat(records.get(0).get("ingestJobId").asString()).isEqualTo(id);

        JsonNode stats = json.readTree(mvc.perform(get("/api/v1/ingest/stats").with(asOwner())).andReturn()
                .getResponse().getContentAsString());
        assertThat(stats.get("jobs").asLong()).isEqualTo(1);
        assertThat(stats.get("recordsLoaded").asLong()).isEqualTo(5);

        mvc.perform(post("/api/v1/ingest/jobs/{id}/retry", id).with(asOwner())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/ingest/jobs/{id}", id).with(asOwner())).andExpect(status().isNoContent());
        assertThat(jobs.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedSample_isPartial_andOdpOutage_isFailedThenRetried() throws Exception {
        when(odpClient.bulkUpsert(eq(DocumentFormat.Target.PATENTS), anyList()))
                .thenAnswer(inv -> new BulkUpsertResponse(((List<JsonNode>) inv.getArgument(1)).size(), 0, List.of()));
        JsonNode partial = awaitStatus(upload("malformed-sample.xml").get("id").asString(), "COMPLETED", "PARTIAL", "FAILED");
        assertThat(partial.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(partial.get("recordsLoaded").asInt()).isEqualTo(1);
        assertThat(partial.get("errors").get(0).get("identifier").asString()).isEqualTo("12345612");

        when(odpClient.bulkUpsert(eq(DocumentFormat.Target.TRADEMARKS), anyList()))
                .thenThrow(new OdpClientException("odp-service unavailable after 3 attempts", null, true))
                .thenAnswer(inv -> new BulkUpsertResponse(((List<JsonNode>) inv.getArgument(1)).size(), 0, List.of()));
        String tm = upload("apc240102-trademark-sample.xml").get("id").asString();
        JsonNode failed = awaitStatus(tm, "COMPLETED", "PARTIAL", "FAILED");
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("message").asString()).contains("unavailable");

        mvc.perform(post("/api/v1/ingest/jobs/{id}/retry", tm).with(asOwner())).andExpect(status().isAccepted());
        JsonNode retried = awaitStatus(tm, "COMPLETED", "PARTIAL", "FAILED");
        assertThat(retried.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(retried.get("recordsLoaded").asInt()).isEqualTo(5);
        assertThat(retried.get("documentFormat").asString()).isEqualTo("TRADEMARK_DAILY");

        JwtRequestPostProcessor stranger = jwt().jwt(j -> j.subject("someone-else").claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(get("/api/v1/ingest/jobs/{id}", tm).with(stranger)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/ingest/jobs/{id}", tm).with(stranger)).andExpect(status().isForbidden());
        assertThat(jobs.findById(UUID.fromString(tm))).get().extracting(j -> j.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void publicEndpoints_andAuthRequired() throws Exception {
        mvc.perform(get("/api/v1/ingest/samples")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ingest/jobs")).andExpect(status().isUnauthorized());
    }
}