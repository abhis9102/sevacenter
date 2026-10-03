package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Per-module access limits (ADR 0021), enforced by the server: NONE closes a module, VIEW makes it
 * read-only, a limit never grants beyond the role, admins can't be limited, only admins set limits,
 * and a change applies on the user's very next request.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ModuleAccessTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private PostgreSQLContainer postgres;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("ma-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void noneClosesAModuleOnTheServerAndTheRestStaysOpen() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        long id = staff.userId(a, member);
        mvc.perform(on(a, get("/api/v1/devotees")).session(member)).andExpect(status().isOk());
        limit(admin, id, "{\"DEVOTEES\":\"NONE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleLimits.DEVOTEES").value("NONE"));
        // Same session, next request: no re-login needed for the limit to bite.
        mvc.perform(on(a, get("/api/v1/devotees")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/events")).session(member)).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/me")).session(member)).andExpect(jsonPath("$.moduleLimits.DEVOTEES").value("NONE"));
    }

    @Test
    void viewMakesAModuleReadOnly() throws Exception {
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        limit(admin, staff.userId(a, leader), "{\"PUJAS\":\"VIEW\"}").andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/pujas")).session(leader)).andExpect(status().isOk());
        mvc.perform(on(a, post("/api/v1/pujas")).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Archana", "dakshina", "0", "active", true, "displayOrder", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/events")).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("title", "Utsav",
                        "startsAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(3).toString(),
                        "endsAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(3).plusHours(2).toString(),
                        "capacity", 10, "registrationOpen", true))).andExpect(status().isCreated());
    }

    @Test
    void aLimitNeverGrantsMoreThanTheRole() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        limit(admin, staff.userId(a, member), "{\"DONATIONS\":\"FULL\",\"VOLUNTEERS\":\"FULL\"}").andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/donations")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/sevaks")).session(member)).andExpect(status().isForbidden());
    }

    @Test
    void adminsCantBeLimitedAndPromotionClearsLimits() throws Exception {
        limit(admin, staff.userId(a, admin), "{\"DEVOTEES\":\"NONE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("admins_not_limited"));
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        long id = staff.userId(a, leader);
        limit(admin, id, "{\"DEVOTEES\":\"NONE\"}").andExpect(status().isOk());
        staff.changeRole(a, admin, id, "TRUST_ADMIN");
        mvc.perform(on(a, get("/api/v1/devotees")).session(leader)).andExpect(status().isOk());
        staff.changeRole(a, admin, id, "LEADER");
        mvc.perform(on(a, get("/api/v1/devotees")).session(leader)).andExpect(status().isOk());
    }

    /** Defence in depth: even limits written straight into the database never bind an admin. */
    @Test
    void storedLimitsOnAnAdminAreIgnored() throws Exception {
        long adminId = staff.userId(a, admin);
        try (java.sql.Connection c = postgres.createConnection("");
             java.sql.PreparedStatement st = c.prepareStatement("update app_user set module_limits = ? where id = ?")) {
            st.setString(1, "DEVOTEES:NONE");
            st.setLong(2, adminId);
            org.assertj.core.api.Assertions.assertThat(st.executeUpdate()).isOne();
        }
        mvc.perform(on(a, get("/api/v1/devotees")).session(admin)).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/me")).session(admin)).andExpect(jsonPath("$.moduleLimits").isEmpty());
    }

    @Test
    void onlyAdminsSetLimitsAndEveryChangeIsAudited() throws Exception {
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        long memberId = staff.userId(a, member);
        limit(leader, memberId, "{\"DEVOTEES\":\"NONE\"}").andExpect(status().isForbidden());
        limit(admin, memberId, "{\"EVENTS\":\"VIEW\",\"DEVOTEES\":\"NONE\"}").andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/audit?action=USER_ACCESS_CHANGED")).session(admin))
                .andExpect(jsonPath("$.items[0].targetId").value(memberId))
                .andExpect(jsonPath("$.items[0].detail").value("DEVOTEES:NONE,EVENTS:VIEW"));
        limit(admin, memberId, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.moduleLimits").isEmpty());
        mvc.perform(on(a, get("/api/v1/devotees")).session(member)).andExpect(status().isOk());
    }

    @Test
    void unknownModulesOrLevelsAreRejected() throws Exception {
        long id = staff.userId(a, staff.staff(a, admin, "MEMBER"));
        limit(admin, id, "{\"PAYMENTS\":\"NONE\"}").andExpect(status().isBadRequest());
        limit(admin, id, "{\"DEVOTEES\":\"ADMIN\"}").andExpect(status().isBadRequest());
    }

    private ResultActions limit(MockHttpSession session, long userId, String limits) throws Exception {
        return mvc.perform(on(a, put("/api/v1/users/" + userId + "/module-access")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"limits\":" + limits + "}"));
    }
}
