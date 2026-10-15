package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationService;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Groups (ADR 0037): staff run them, devotees join listed ones, nobody sees others' members. */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class})
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1", "sevacenter.mail.from=no-reply@test.example"})
@AutoConfigureMockMvc
class GroupTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private DevoteeLoginTest.Senders senders;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("grp-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aDevoteeJoinsAnOpenGroupAndOthersSeeOnlyACount() throws Exception {
        long g = group(a, "Sunday Satsang", "PUBLIC", "OPEN", null);
        Cookie lakshmi = devotee(a, "lakshmi@" + a + ".example");
        join(a, g, lakshmi).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        join(a, g, lakshmi).andExpect(status().isOk()); // idempotent

        String listed = mvc.perform(on(a, get("/api/v1/public/groups"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sunday Satsang"))
                .andExpect(jsonPath("$[0].members").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain("lakshmi");

        mvc.perform(on(a, get("/api/v1/portal/groups")).cookie(lakshmi))
                .andExpect(jsonPath("$[0].name").value("Sunday Satsang")).andExpect(jsonPath("$[0].status").value("ACTIVE"));
        mvc.perform(on(a, delete("/api/v1/portal/groups/" + g)).cookie(lakshmi).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/portal/groups")).cookie(lakshmi)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anApprovalGroupTurnsAJoinIntoARequestALeaderApproves() throws Exception {
        long g = group(a, "Youth Seva", "PUBLIC", "APPROVAL", null);
        Cookie ravi = devotee(a, "ravi@" + a + ".example");
        join(a, g, ravi).andExpect(jsonPath("$.status").value("PENDING"));
        long memberId = json.readTree(mvc.perform(on(a, get("/api/v1/groups/" + g + "/members")).session(admin))
                .andExpect(jsonPath("$[0].contact").value("ravi@" + a + ".example"))
                .andReturn().getResponse().getContentAsString()).at("/0/id").asLong();
        mvc.perform(on(a, post("/api/v1/groups/" + g + "/members/" + memberId + "/approve")).session(admin).with(csrf()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(on(a, get("/api/v1/portal/groups")).cookie(ravi)).andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void aFullGroupTakesNoMore() throws Exception {
        long g = group(a, "Bhajan Mandali", "PUBLIC", "OPEN", 1);
        join(a, g, devotee(a, "one@" + a + ".example")).andExpect(status().isOk());
        join(a, g, devotee(a, "two@" + a + ".example")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("group_full"));
        mvc.perform(on(a, get("/api/v1/public/groups"))).andExpect(jsonPath("$[0].full").value(true));
    }

    @Test
    void aPrivateGroupIsHiddenAndOnlyStaffAddMembers() throws Exception {
        long g = group(a, "Trustees' study circle", "PRIVATE", "OPEN", null);
        String email = "meera@" + a + ".example";
        Cookie meera = devotee(a, email);
        mvc.perform(on(a, get("/api/v1/public/groups"))).andExpect(jsonPath("$.length()").value(0));
        join(a, g, meera).andExpect(status().isNotFound());

        addMember(a, g, "someone-new@" + a + ".example").andExpect(status().isBadRequest()); // never signed in
        addMember(a, g, email).andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/portal/groups")).cookie(meera))
                .andExpect(jsonPath("$[0].name").value("Trustees' study circle"));
    }

    @Test
    void membersOnlySeeTheListLeadersRunTheGroups() throws Exception {
        long g = group(a, "Mahila Mandal", "PUBLIC", "OPEN", null);
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(on(a, get("/api/v1/groups")).session(member)).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/groups/" + g + "/members")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/groups")).session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("X", "PUBLIC", "OPEN", null))).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/portal/groups"))).andExpect(status().isUnauthorized()); // no devotee cookie
    }

    @Test
    void anotherTrustsDevoteeCantJoin() throws Exception {
        long g = group(a, "Gita path", "PUBLIC", "OPEN", null);
        String b = staff.tenant("grp-b");
        Cookie fromB = devotee(b, "x@" + b + ".example");
        join(a, g, fromB).andExpect(status().isUnauthorized()); // the cookie is B's session: not on A's host
        join(b, g, fromB).andExpect(status().isNotFound());      // and A's group doesn't exist on B
    }

    // --- helpers ------------------------------------------------------------------------------

    private long group(String slug, String name, String visibility, String policy, Integer capacity) throws Exception {
        return json.readTree(mvc.perform(on(slug, post("/api/v1/groups")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, visibility, policy, capacity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private String body(String name, String visibility, String policy, Integer capacity) {
        return staff.body("name", name, "category", "SATSANG", "meets", "Sundays 6 pm", "visibility", visibility,
                "joinPolicy", policy, "capacity", capacity);
    }

    private ResultActions join(String slug, long g, Cookie devotee) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/portal/groups/" + g + "/join")).cookie(devotee).with(csrf()));
    }

    private ResultActions addMember(String slug, long g, String email) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/groups/" + g + "/members")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("channel", "EMAIL", "contact", email)));
    }

    /** Signs a devotee in through the real code flow and returns their cookie. */
    private Cookie devotee(String slug, String email) throws Exception {
        String ip = "10.50." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        mvc.perform(fromIp(on(slug, post("/api/v1/public/devotee-login/code")), ip).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("channel", "EMAIL", "contact", email)))
                .andExpect(status().isAccepted());
        String code = senders.email.last();
        String value = mvc.perform(fromIp(on(slug, post("/api/v1/public/devotee-login/verify")), ip).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("channel", "EMAIL", "contact", email, "code", code)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SC_DEVOTEE").getValue();
        return new Cookie("SC_DEVOTEE", value);
    }

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder r, String ip) {
        return r.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }
}
