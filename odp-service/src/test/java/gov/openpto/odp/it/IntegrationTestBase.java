package gov.openpto.odp.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full application against the shared {@code openpto_test} database (schema {@code odp} only).
 * Tests create uniquely named rows and delete them afterwards, so they are safe to re-run and to
 * run next to other services' tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    static final String INTERNAL_TOKEN = "dev-internal-token-change-me";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    /** A random lowercase word that no seeded/mock text contains (a stable FTS lexeme). */
    static String uniqueWord() {
        StringBuilder sb = new StringBuilder("zq");
        for (int i = 0; i < 10; i++) {
            sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return sb.toString();
    }

    static String digits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(i == 0 ? 1 : 0, 10));
        }
        return sb.toString();
    }

    MockHttpServletRequestBuilder postJson(String url, Object body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    MockHttpServletRequestBuilder internal(String url, Object body) {
        return postJson(url, body).header("X-Internal-Token", INTERNAL_TOKEN);
    }

    JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
