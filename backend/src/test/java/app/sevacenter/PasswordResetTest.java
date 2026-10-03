package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    private static final String OLD_PASSWORD = "initial-password-123";
    private static final String NEW_PASSWORD = "brand-new-secret-password-456";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(100);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private JsonMapper json;

    private String slug;
    private String email;
    private String ip;

    @BeforeEach
    void setUp() {
        ip = "10.8." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        slug = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        email = "admin@" + slug + ".example";
        registration.register(new RegistrationRequest(slug, "Trust " + slug, email, OLD_PASSWORD, "Admin"));
    }

    @Test
    void requestResetForValidUserReturnsDevTokenAndAllowsPasswordReset() throws Exception {
        // 1. Request reset
        MvcResult res = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.devToken").exists())
                .andReturn();

        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        String devToken = (String) body.get("devToken");
        assertThat(devToken).isNotBlank();

        // 2. Reset password
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", devToken, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        // 3. Old password no longer works
        mvc.perform(on(slug, post("/api/v1/auth/login"))
                        .with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized());

        // 4. New password works
        mvc.perform(on(slug, post("/api/v1/auth/login"))
                        .with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void requestResetForNonExistentUserDoesNotRevealEmailAbsence() throws Exception {
        mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.devToken").doesNotExist());
    }

    @Test
    void tokenCannotBeReused() throws Exception {
        MvcResult res = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();

        String token = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("devToken");
        assertThat(token).isNotNull();

        // First use: ok
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        // Second use: rejected (invalid_or_expired_link)
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", "another-new-password-789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_or_expired_link"));
    }

    @Test
    void requestingResetAgainInvalidatesPriorToken() throws Exception {
        // Token 1
        MvcResult res1 = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();
        String token1 = (String) json.readValue(res1.getResponse().getContentAsString(), Map.class).get("devToken");
        assertThat(token1).isNotNull();

        // Token 2
        MvcResult res2 = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = (String) json.readValue(res2.getResponse().getContentAsString(), Map.class).get("devToken");
        assertThat(token2).isNotNull();

        // Token 1 should be invalid
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token1, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_or_expired_link"));

        // Token 2 succeeds
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token2, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordMustMeetLengthRequirements() throws Exception {
        MvcResult res = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();

        String token = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("devToken");
        assertThat(token).isNotNull();

        // Short password (< 12 chars)
        mvc.perform(on(slug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    void tokenFromAnotherTenantCannotBeRedeemed() throws Exception {
        String otherSlug = "other-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(otherSlug, "Other Trust", "other@" + otherSlug + ".example", OLD_PASSWORD, "Other Admin"));

        MvcResult res = mvc.perform(on(slug, post("/api/v1/auth/forgot-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();

        String token = (String) json.readValue(res.getResponse().getContentAsString(), Map.class).get("devToken");
        assertThat(token).isNotNull();

        // Attempting to redeem on otherSlug's host fails due to tenant isolation RLS
        mvc.perform(on(otherSlug, post("/api/v1/auth/reset-password"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_or_expired_link"));
    }

    private static MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }
}
