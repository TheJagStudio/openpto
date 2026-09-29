package gov.openpto.odp.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.odp.dto.AuthResponse;
import gov.openpto.odp.dto.UserResponse;
import gov.openpto.odp.exception.AccountLockedException;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.exception.InvalidCredentialsException;
import gov.openpto.odp.service.AuthService;

import java.time.Duration;
import java.time.Instant;
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

@WebMvcTest(AuthController.class)
@Import(WebSliceConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService auth;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void register_valid_returns201() throws Exception {
        UserResponse user = new UserResponse(UUID.randomUUID(), "a@b.co", "A", List.of("USER"), Instant.now());
        given(auth.register(any())).willReturn(AuthResponse.bearer("tok", 28800, user));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"abcdefghi1\",\"displayName\":\"A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(28800))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"));
    }

    @Test
    void register_weakPasswordAndBadEmail_returns400WithAllFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope\",\"password\":\"onlyletters\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("displayName")));
        verifyNoInteractions(auth);
    }

    @Test
    void register_duplicate_returns409() throws Exception {
        given(auth.register(any())).willThrow(new ConflictException("An account with this email already exists"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"abcdefghi1\",\"displayName\":\"A\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void login_badCredentials_returns401SameMessage() throws Exception {
        given(auth.login(any())).willThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_locked_returns429WithRetryAfter() throws Exception {
        given(auth.login(any())).willThrow(new AccountLockedException(Duration.ofMinutes(15)));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"whatever1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
