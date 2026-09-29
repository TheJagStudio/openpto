package gov.openpto.odp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.odp.dto.ApiKeyResponse;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.model.ApiTier;
import gov.openpto.odp.service.ApiKeyService;
import gov.openpto.odp.service.UsageService;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(WebSliceConfig.class)
class AccountControllerTest {

    static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ApiKeyService apiKeys;

    @MockitoBean
    UsageService usage;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void listKeys_withoutToken_returns401Problem() throws Exception {
        mvc.perform(get("/api/v1/account/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void createKey_valid_returns201WithPlaintextOnce() throws Exception {
        UUID id = UUID.randomUUID();
        given(apiKeys.create(eq(USER), any())).willReturn(new ApiKeyResponse(
                id, "nb", "opto_ab12", ApiTier.FREE, Instant.parse("2026-01-01T00:00:00Z"), null, null, 0, "opto_secret"));

        mvc.perform(post("/api/v1/account/api-keys")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nb\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("opto_secret"))
                .andExpect(jsonPath("$.prefix").value("opto_ab12"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void createKey_blankName_returns400() throws Exception {
        mvc.perform(post("/api/v1/account/api-keys")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void createKey_limitReached_returns409() throws Exception {
        given(apiKeys.create(eq(USER), any())).willThrow(new ConflictException("A maximum of 5 active API keys is allowed"));

        mvc.perform(post("/api/v1/account/api-keys")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sixth\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createKey_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/account/api-keys")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void rotate_unknownKey_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(apiKeys.rotate(USER, id)).willThrow(NotFoundException.of("API key", id));

        mvc.perform(post("/api/v1/account/api-keys/{id}/rotate", id).with(jwt().jwt(j -> j.subject(USER.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void revoke_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/account/api-keys/{id}", id).with(jwt().jwt(j -> j.subject(USER.toString()))))
                .andExpect(status().isNoContent());
        verify(apiKeys).revoke(USER, id);
    }

    @Test
    void usage_daysOutOfRange_returns400() throws Exception {
        mvc.perform(get("/api/v1/account/usage").param("days", "0").with(jwt().jwt(j -> j.subject(USER.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"));
    }

    @Test
    void usage_nonUuidSubject_returns401() throws Exception {
        mvc.perform(get("/api/v1/account/usage").with(jwt().jwt(j -> j.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized());
    }
}
