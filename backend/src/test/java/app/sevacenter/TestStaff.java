package app.sevacenter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test fixture for feature tests: registers tenants and logs in staff of any role through the
 * real flows (registration, admin creates user, setup link, form login), so sessions carry
 * exactly what production sessions carry.
 */
final class TestStaff {

    static final String PASSWORD = "correct-horse-battery-staple";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private final MockMvc mvc;
    private final JsonMapper json;
    private final RegistrationService registration;
    // Each fixture logs in from its own IP, and 10.10.* is used by no other test (StaffAuthTest
    // locks out IPs on purpose), so the per-IP login throttle never couples tests.
    private final String ip = "10.10." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);

    TestStaff(MockMvc mvc, JsonMapper json, RegistrationService registration) {
        this.mvc = mvc;
        this.json = json;
        this.registration = registration;
    }

    /** A new tenant; its admin is {@code admin@<slug>.example}. */
    String tenant(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(slug, "Trust " + slug, admin(slug), PASSWORD, "Admin"));
        return slug;
    }

    static String admin(String slug) {
        return "admin@" + slug + ".example";
    }

    MockHttpSession loginAdmin(String slug) throws Exception {
        return login(slug, admin(slug));
    }

    /** Creates an active staff member with {@code role} and returns their logged-in session. */
    MockHttpSession staff(String slug, MockHttpSession admin, String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@x.example";
        JsonNode created = json.readTree(mvc.perform(on(slug, post("/api/v1/users")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "displayName", "Staff", "role", role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = created.at("/setupUrl").asString();
        String token = url.substring(url.indexOf("#token=") + "#token=".length());
        mvc.perform(on(slug, post("/api/v1/auth/setup")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("token", token, "password", PASSWORD))).andExpect(status().isNoContent());
        return login(slug, email);
    }

    long userId(String slug, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(on(slug, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me")).session(session))
                .andReturn().getResponse().getContentAsString()).at("/userId").asLong();
    }

    void changeRole(String slug, MockHttpSession admin, long userId, String role) throws Exception {
        mvc.perform(on(slug, patch("/api/v1/users/" + userId + "/role")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("role", role))).andExpect(status().isOk());
    }

    /** A login attempt with any password; the caller checks the status. */
    org.springframework.test.web.servlet.ResultActions login(String slug, String email, String password) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .param("email", email).param("password", password));
    }

    MockHttpSession login(String slug, String email) throws Exception {
        return (MockHttpSession) mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    String body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    static MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }
}
