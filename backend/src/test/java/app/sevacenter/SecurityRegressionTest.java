package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every DAST (G5) finding becomes a test here, so a fix can't silently regress. Cookie flags
 * are asserted over a real server in SessionCookieTest: spring-security-test's csrf()
 * post-processor swaps the shared CSRF repository for one that never writes cookies, so a
 * MockMvc cookie check here failed depending on which test class ran first.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRegressionTest {

    private static final String INVALID_REGISTRATION =
            "{\"slug\":\"X\",\"trustName\":\"\",\"adminEmail\":\"nope\",\"adminPassword\":\"short\",\"adminName\":\"\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/ping"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void identicalInvalidRequestsGetByteIdenticalResponses() throws Exception {
        String first = rejected();
        for (int i = 0; i < 10; i++) {
            assertThat(rejected()).isEqualTo(first);
        }
    }

    @Test
    void openApiSpecDoesNotInviteTokensInUrls() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/csrf'].get.parameters").doesNotExist());
    }

    private String rejected() throws Exception {
        return mvc.perform(post("/api/v1/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_REGISTRATION))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
    }
}
