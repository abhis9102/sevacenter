package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Staff login (ADR 0009): per-tenant host, server session, tenant binding, brute-force
 * throttling. Each test uses its own client IP so the shared throttle can't leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StaffAuthTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistrationService registration;

    private String a;          // tenant slugs
    private String b;
    private String ip;         // this test's client IP

    @BeforeEach
    void twoTenants() {
        a = register("auth-a");
        b = register("auth-b");
        ip = "10.9." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    @Test
    void staffLogInOnTheirOwnTenantHost() throws Exception {
        MockHttpSession session = login(a, admin(a), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(admin(a)))
                .andExpect(jsonPath("$.role").value("TRUST_ADMIN"))
                .andExpect(jsonPath("$.tenant").value(a))
                .andReturn().getRequest().getSession() instanceof MockHttpSession s ? s : null;

        mvc.perform(on(a, get("/api/v1/me")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(admin(a)));
    }

    @Test
    void sessionIdChangesAtLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        mvc.perform(on(a, post("/api/v1/auth/login")).session(session).with(csrf())
                        .param("email", admin(a)).param("password", PASSWORD))
                .andExpect(status().isOk());
        assertThat(session.getId()).as("session fixation protection").isNotEqualTo(before);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        String wrongPassword = login(a, admin(a), "not-the-password-123")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = login(a, "nobody@example.org", PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(unknownEmail).isEqualTo(wrongPassword).contains("invalid_credentials");
    }

    @Test
    void staffCannotLogInOnAnotherTenantsHost() throws Exception {
        login(b, admin(a), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void noTenantHostMeansNoLogin() throws Exception {
        mvc.perform(post("http://localhost/api/v1/auth/login").with(csrf()).with(clientIp())
                        .param("email", admin(a)).param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionIsWorthlessOnAnotherTenantsHostAndIsDestroyed() throws Exception {
        MockHttpSession session = sessionFor(a);
        mvc.perform(on(b, get("/api/v1/me")).session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).as("the replayed session is invalidated").isTrue();
    }

    @Test
    void loginRequiresCsrf() throws Exception {
        mvc.perform(on(a, post("/api/v1/auth/login")).param("email", admin(a)).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = sessionFor(a);
        mvc.perform(on(a, post("/api/v1/auth/logout")).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/me")).session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedGetsJson401WithoutABasicAuthChallenge() throws Exception {
        mvc.perform(on(a, get("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void accountLocksAfterRepeatedFailuresEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(a, admin(a), "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(a, admin(a), PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmailsLockTheSameWay() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(a, "ghost@example.org", "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(a, "ghost@example.org", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void oneIpSprayingManyAccountsGetsLocked() throws Exception {
        // Below the per-account limit for each account, so only the IP limit can trip.
        for (int i = 0; i < LoginThrottle.MAX_IP_FAILURES; i++) {
            login(a, "user" + i + "@example.org", "wrong-password-1").andExpect(status().isUnauthorized());
        }
        login(b, admin(b), PASSWORD).andExpect(status().isTooManyRequests());
    }

    /** A shared IP (carrier NAT) isn't locked by a handful of typos from other people on it. */
    @Test
    void aFewFailuresFromASharedIpDontLockItsOtherUsers() throws Exception {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            login(a, "user" + i + "@example.org", "wrong-password-1").andExpect(status().isUnauthorized());
        }
        login(b, admin(b), PASSWORD).andExpect(status().isOk());
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResultActions login(String slug, String email, String password) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf()).with(clientIp())
                .param("email", email).param("password", password));
    }

    private MockHttpSession sessionFor(String slug) throws Exception {
        return (MockHttpSession) login(slug, admin(slug), PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    private static MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setServerName(slug + ".sevacenter.app");
            return r;
        });
    }

    private RequestPostProcessor clientIp() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private String register(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(slug, "Trust " + slug, admin(slug), PASSWORD, "Admin"));
        return slug;
    }

    private static String admin(String slug) {
        return "admin@" + slug + ".example";
    }
}
