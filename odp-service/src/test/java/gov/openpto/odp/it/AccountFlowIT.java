package gov.openpto.odp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.JsonNode;

class AccountFlowIT extends IntegrationTestBase {

    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // ON DELETE CASCADE removes roles, API keys and usage rows.
        emails.forEach(e -> jdbc.update("DELETE FROM users WHERE email = ?", e));
    }

    private String register(String password) throws Exception {
        String email = "it-" + uniqueWord() + "@example.com";
        emails.add(email);
        mvc.perform(postJson("/api/v1/auth/register", Map.of("email", email, "password", password, "displayName", "IT User")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"));
        return email;
    }

    private String login(String email, String password) throws Exception {
        JsonNode r = body(mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn());
        return r.get("accessToken").asString();
    }

    @Test
    void register_login_createKey_verify_reportUsage_rotate_revoke() throws Exception {
        String email = register("integration-pass-1");
        String token = login(email.toUpperCase(), "integration-pass-1");
        String bearer = "Bearer " + token;

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        JsonNode created = body(mvc.perform(postJson("/api/v1/account/api-keys", Map.of("name", "it key"))
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn());
        String keyId = created.get("id").asString();
        String plaintext = created.get("key").asString();
        assertThat(plaintext).matches("^opto_[0-9A-Za-z]{40}$");
        assertThat(created.get("prefix").asString()).isEqualTo(plaintext.substring(0, 9));

        mvc.perform(internal("/internal/v1/api-keys/verify", Map.of("key", plaintext)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.keyId").value(keyId))
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.perMinute").value(120))
                .andExpect(jsonPath("$.perDay").value(20000));

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        Map<String, Object> usage = Map.of("entries", List.of(
                Map.of("keyId", keyId, "date", today, "count", 7),
                Map.of("keyId", keyId, "date", yesterday, "count", 3),
                Map.of("keyId", "00000000-0000-0000-0000-000000000000", "date", today, "count", 99)));
        mvc.perform(internal("/internal/v1/usage", usage)).andExpect(status().isNoContent());
        // A second flush for the same day adds up (ON CONFLICT ... DO UPDATE SET count = count + excluded.count).
        mvc.perform(internal("/internal/v1/usage", Map.of("entries", List.of(Map.of("keyId", keyId, "date", today, "count", 5)))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/account/usage").param("days", "7").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(15))
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.daily[6].date").value(today))
                .andExpect(jsonPath("$.daily[6].requests").value(12))
                .andExpect(jsonPath("$.daily[5].requests").value(3))
                .andExpect(jsonPath("$.byKey[0].keyId").value(keyId))
                .andExpect(jsonPath("$.byKey[0].requests").value(15));

        mvc.perform(get("/api/v1/account/api-keys").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestCount").value(15))
                .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].key").doesNotExist());

        JsonNode rotated = body(mvc.perform(post("/api/v1/account/api-keys/{id}/rotate", keyId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn());
        String newKey = rotated.get("key").asString();
        assertThat(newKey).isNotEqualTo(plaintext);
        mvc.perform(internal("/internal/v1/api-keys/verify", Map.of("key", plaintext))).andExpect(jsonPath("$.valid").value(false));
        mvc.perform(internal("/internal/v1/api-keys/verify", Map.of("key", newKey))).andExpect(jsonPath("$.valid").value(true));

        mvc.perform(delete("/api/v1/account/api-keys/{id}", keyId).header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(internal("/internal/v1/api-keys/verify", Map.of("key", newKey)))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.keyId").isEmpty());
        mvc.perform(post("/api/v1/account/api-keys/{id}/rotate", keyId).header("Authorization", bearer))
                .andExpect(status().isConflict());
    }

    @Test
    void sixthActiveKey_isRejectedWith409_untilOneIsRevoked() throws Exception {
        String email = register("integration-pass-2");
        String bearer = "Bearer " + login(email, "integration-pass-2");
        String first = null;
        for (int i = 0; i < 5; i++) {
            JsonNode k = body(mvc.perform(postJson("/api/v1/account/api-keys", Map.of("name", "k" + i)).header("Authorization", bearer))
                    .andExpect(status().isCreated())
                    .andReturn());
            first = first == null ? k.get("id").asString() : first;
        }
        mvc.perform(postJson("/api/v1/account/api-keys", Map.of("name", "k6")).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("5 active API keys")));

        mvc.perform(delete("/api/v1/account/api-keys/{id}", first).header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(postJson("/api/v1/account/api-keys", Map.of("name", "k6")).header("Authorization", bearer))
                .andExpect(status().isCreated());
    }

    @Test
    void fiveFailedLogins_lockTheAccount() throws Exception {
        String email = register("integration-pass-3");
        for (int i = 1; i <= 4; i++) {
            mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-" + i)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-5")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        // Even the right password is refused while locked.
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", "integration-pass-3")))
                .andExpect(status().isTooManyRequests());
        Integer failed = jdbc.queryForObject("SELECT failed_login_count FROM users WHERE email = ?", Integer.class, email);
        assertThat(failed).isZero();
    }

    @Test
    void unknownEmail_getsSameMessageAsWrongPassword() throws Exception {
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", "nobody-" + uniqueWord() + "@example.com", "password", "x1234567890")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void issuedTokens_validateAgainstPublishedJwks_likeTheGateway() throws Exception {
        String email = register("integration-pass-4");
        String token = login(email, "integration-pass-4");

        String jwks = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                com.nimbusds.jose.JWSAlgorithm.RS256, new ImmutableJWKSet<>(JWKSet.parse(jwks))));
        Jwt jwt = new NimbusJwtDecoder(processor).decode(token);

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("openpto");
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getHeaders()).containsKey("kid");
    }
}
