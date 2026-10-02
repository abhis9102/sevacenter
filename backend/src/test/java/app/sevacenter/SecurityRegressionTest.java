package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
 * Every DAST (G5) finding becomes a test here, so a fix can't silently regress. DAST alone
 * can't hold the line: once the scan sends a CSRF cookie, the server stops re-issuing it and
 * ZAP's passive cookie checks no longer see the flags.
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
    void csrfCookieIsSameSiteStrictButReadableByOurFrontend() throws Exception {
        mvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().sameSite("XSRF-TOKEN", "Strict"))
                // Deliberately not HttpOnly: the SPA echoes it in X-XSRF-TOKEN (ADR 0007).
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

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
