package gov.openpto.odp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.odp.dto.BulkUpsertResponse;
import gov.openpto.odp.dto.VerifyKeyResponse;
import gov.openpto.odp.model.ApiTier;
import gov.openpto.odp.service.ApiKeyService;
import gov.openpto.odp.service.BulkUpsertService;
import gov.openpto.odp.service.UsageService;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalController.class)
@Import(WebSliceConfig.class)
class InternalControllerTest {

    static final String TOKEN = "dev-internal-token-change-me";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ApiKeyService apiKeys;

    @MockitoBean
    UsageService usage;

    @MockitoBean
    BulkUpsertService bulkUpsert;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void verify_missingInternalToken_returns401Problem() throws Exception {
        mvc.perform(post("/internal/v1/api-keys/verify").contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid X-Internal-Token header is required"));
        verifyNoInteractions(apiKeys);
    }

    @Test
    void verify_wrongInternalToken_returns401() throws Exception {
        mvc.perform(post("/internal/v1/api-keys/verify")
                        .header("X-Internal-Token", TOKEN + "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_bearerTokenIsNotEnough_returns401() throws Exception {
        mvc.perform(post("/internal/v1/api-keys/verify")
                        .header("Authorization", "Bearer abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_validToken_returnsLimits() throws Exception {
        UUID keyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(apiKeys.verify("opto_k")).willReturn(VerifyKeyResponse.valid(keyId, userId, ApiTier.FREE));

        mvc.perform(post("/internal/v1/api-keys/verify")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"opto_k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.keyId").value(keyId.toString()))
                .andExpect(jsonPath("$.perMinute").value(120))
                .andExpect(jsonPath("$.perDay").value(20000));
    }

    @Test
    void usage_valid_returns204() throws Exception {
        mvc.perform(post("/internal/v1/usage")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"keyId\":\"" + UUID.randomUUID() + "\",\"date\":\"2026-09-28\",\"count\":3}]}"))
                .andExpect(status().isNoContent());
        verify(usage).record(any());
    }

    @Test
    void usage_negativeCount_returns400() throws Exception {
        mvc.perform(post("/internal/v1/usage")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"keyId\":\"" + UUID.randomUUID() + "\",\"date\":\"2026-09-28\",\"count\":-1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].count"));
    }

    @Test
    void bulkUpsert_passesRawRecordsThrough() throws Exception {
        given(bulkUpsert.upsertPatents(anyList())).willReturn(new BulkUpsertResponse(1, 0, List.of()));

        mvc.perform(post("/internal/v1/patents/bulk-upsert")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"patentNumber\":\"US1B2\",\"title\":\"t\",\"type\":\"UTILITY\",\"filingDate\":\"2020-01-01\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.failed").isArray());
    }

    @Test
    void bulkUpsert_notAnArray_returns400() throws Exception {
        mvc.perform(post("/internal/v1/trademarks/bulk-upsert")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"97123456\"}"))
                .andExpect(status().isBadRequest());
    }
}
