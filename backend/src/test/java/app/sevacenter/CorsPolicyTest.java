package app.sevacenter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * No cross-origin grants to arbitrary sites. A permissive CORS config (any origin, with
 * credentials) lets any website make logged-in requests and read the responses.
 *
 * <p>Lives here because DAST can't see it: ZAP's API scan sends no Origin header, and its active
 * CORS rule isn't in the installed rule set (G5 validation). Allowed origins, when the frontend
 * needs them, get an explicit allowlist and a test case here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsPolicyTest {

    private static final String EVIL = "https://evil.example";

    @Autowired
    private MockMvc mvc;

    @Test
    void arbitraryOriginGetsNoCorsGrant() throws Exception {
        mvc.perform(get("/api/v1/ping").header("Origin", EVIL))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void arbitraryOriginPreflightIsNotGranted() throws Exception {
        mvc.perform(options("/api/v1/register")
                        .header("Origin", EVIL)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-XSRF-TOKEN, Content-Type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
