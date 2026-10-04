package app.sevacenter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Sevak (volunteer) signups (ADR 0015): public signup, leader review, contact privacy, limits. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SevakTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("sv-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        ip = "10.13." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    @Test
    void anyoneCanOfferSevaAndLearnsNothingBack() throws Exception {
        String body = signUp(a, "Ravi Kumar", "98765 43210").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).at("/name").asString()).isEqualTo("Ravi Kumar");
        assertThat(body).doesNotContain("9876543210", "\"id\"", "status");
    }

    @Test
    void onlyLeadersSeeSignupsWithContactsAndReviewThem() throws Exception {
        signUp(a, "Ravi Kumar", "98765 43210");
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(on(a, get("/api/v1/sevaks")).session(member)).andExpect(status().isForbidden());
        long id = json.readTree(mvc.perform(on(a, get("/api/v1/sevaks")).session(leader)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phone").value("+919876543210"))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andReturn().getResponse().getContentAsString()).get(0).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/sevaks/" + id + "/approve")).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/sevaks/" + id + "/approve")).session(leader).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void aSignupNeedsAContactAndCantApproveItself() throws Exception {
        mvc.perform(on(a, post("/api/v1/public/sevak")).with(csrf()).with(fromIp())
                        .contentType(MediaType.APPLICATION_JSON).content(staff.body("fullName", "No Contact", "sevaAreas", "Kitchen")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.phone").exists());
        mvc.perform(on(a, post("/api/v1/public/sevak")).with(csrf()).with(fromIp()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", "Sneaky", "phone", "9876543210", "sevaAreas", "Kitchen",
                        "status", "APPROVED", "reviewedBy", 1))).andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/sevaks")).session(leader)).andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void anotherTrustsSignupsAreInvisibleAndUnreviewable() throws Exception {
        String b = staff.tenant("sv-b");
        signUp(b, "Tenant B Sevak", "98765 43210");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, get("/api/v1/sevaks")).session(adminB))
                .andReturn().getResponse().getContentAsString()).get(0).at("/id").asLong();
        mvc.perform(on(a, get("/api/v1/sevaks")).session(leader)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(on(a, post("/api/v1/sevaks/" + theirs + "/approve")).session(leader).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void signupsAreRateLimitedPerClient() throws Exception {
        for (int i = 0; i < 20; i++) {
            signUp(a, "Sevak " + i, "98765 43210").andExpect(status().isCreated());
        }
        signUp(a, "One too many", "98765 43210").andExpect(status().isTooManyRequests());
    }

    @Test
    void signupsAreNeverDeleted() throws Exception {
        signUp(a, "Ravi", "98765 43210");
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantA));
            jdbc.update("delete from sevak_signup");
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    // --- Sevak Hub: teams, staff registration, assignment, removal (ADR 0028) -----------------------

    @Test
    void leadersCreateEditAndDeleteSevaTeamsWithShifts() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        team(member, "Annadanam kitchen").andExpect(status().isForbidden());
        long id = id(team(leader, "Annadanam kitchen").andExpect(status().isCreated()));
        team(leader, "annadanam KITCHEN").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        mvc.perform(on(a, get("/api/v1/seva-teams")).session(leader)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shifts.length()").value(2))
                .andExpect(jsonPath("$[0].shifts[0].name").value("Pratah preparation"))
                .andExpect(jsonPath("$[0].targetCount").value(15));
        mvc.perform(on(a, put("/api/v1/seva-teams/" + id)).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Annadanam kitchen", "targetCount", 20, "shifts", List.of(
                        Map.of("name", "All day", "startsAt", "06:00", "endsAt", "22:00")))))
                .andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/seva-teams")).session(leader)).andExpect(jsonPath("$[0].shifts.length()").value(1));
        // A deleted team disappears, and its name can be used again.
        mvc.perform(on(a, delete("/api/v1/seva-teams/" + id)).session(leader).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/seva-teams")).session(leader)).andExpect(jsonPath("$.length()").value(0));
        team(leader, "Annadanam kitchen").andExpect(status().isCreated());
        mvc.perform(on(a, delete("/api/v1/seva-teams/" + id)).session(leader).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void staffRegisterAndAssignVolunteersAndDeletingATeamReleasesThem() throws Exception {
        long kitchen = id(team(leader, "Annadanam kitchen"));
        long ravi = id(register(leader, "Ravi Kumar", kitchen).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.teamId").value(kitchen))
                .andExpect(jsonPath("$.registeredByStaff").value(true)));
        signUp(a, "Snehal Pawar", "98765 43211").andExpect(status().isCreated());
        long snehal = json.readTree(mvc.perform(on(a, get("/api/v1/sevaks")).session(leader)).andReturn().getResponse()
                .getContentAsString()).get(0).at("/id").asLong();
        // Placing a new offer in a team approves it.
        assign(snehal, kitchen).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.duty").value("Morning kitchen"));
        mvc.perform(on(a, delete("/api/v1/seva-teams/" + kitchen)).session(leader).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/sevaks")).session(leader))
                .andExpect(jsonPath("$[?(@.teamId != null)]").isEmpty());
        assign(ravi, kitchen).andExpect(status().isNotFound());
    }

    @Test
    void aRemovedVolunteerIsGoneFromEveryListButKeptForTheAuditTrail() throws Exception {
        long ravi = id(register(leader, "Ravi Kumar", null));
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(on(a, delete("/api/v1/sevaks/" + ravi)).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(on(a, delete("/api/v1/sevaks/" + ravi)).session(leader).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/sevaks")).session(leader)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(on(a, delete("/api/v1/sevaks/" + ravi)).session(leader).with(csrf())).andExpect(status().isNotFound());
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        Integer kept = tx.execute(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return jdbc.queryForObject("select count(*) from sevak_signup where removed_at is not null", Integer.class);
        });
        assertThat(kept).isEqualTo(1);
    }

    @Test
    void anotherTrustsTeamCannotBeUsed() throws Exception {
        String b = staff.tenant("sv-b");
        MockHttpSession otherAdmin = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, post("/api/v1/seva-teams")).session(otherAdmin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "Their team")))
                .andReturn().getResponse().getContentAsString()).at("/id").asLong();
        register(leader, "Ravi Kumar", theirs).andExpect(status().isNotFound());
        mvc.perform(on(a, delete("/api/v1/seva-teams/" + theirs)).session(leader).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/seva-teams")).session(leader)).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions team(MockHttpSession session, String name) throws Exception {
        return mvc.perform(on(a, post("/api/v1/seva-teams")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", name, "description", "Cooking and serving mahaprasad", "targetCount", 15,
                        "shifts", List.of(Map.of("name", "Pratah preparation", "startsAt", "06:00", "endsAt", "12:00"),
                                Map.of("name", "Sandhya bhandara", "startsAt", "17:00", "endsAt", "22:00")))));
    }

    private ResultActions register(MockHttpSession session, String name, Long teamId) throws Exception {
        return mvc.perform(on(a, post("/api/v1/sevaks")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", name, "phone", "98765 43212", "sevaAreas", "Kitchen", "teamId", teamId)));
    }

    private ResultActions assign(long id, long teamId) throws Exception {
        return mvc.perform(on(a, post("/api/v1/sevaks/" + id + "/assign")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("teamId", teamId, "duty", "Morning kitchen")));
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private ResultActions signUp(String slug, String name, String phone) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/public/sevak")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", name, "phone", phone, "sevaAreas", "Annadanam kitchen", "availability", "Weekends")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor fromIp() {
        return r -> { r.setRemoteAddr(ip); return r; };
    }
}
