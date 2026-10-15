package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** My family (ADR 0039): devotees run their own household; adults join only by accepting. */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class})
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1", "sevacenter.mail.from=no-reply@test.example"})
@AutoConfigureMockMvc
class FamilyTest {

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

    @BeforeEach
    void setUp() {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("fam-a");
    }

    @Test
    void aFamilyIsStartedChildrenAddedAndAnAdultJoinsByAcceptingTheirInvite() throws Exception {
        Cookie ramesh = signIn("ramesh@" + a + ".example");
        call(ramesh, post("/api/v1/portal/family"), staff.body("yourName", "Ramesh Kulkarni", "familyName", "Kulkarni family"))
                .andExpect(jsonPath("$.myRelation").value("HEAD")).andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.members.length()").value(1));

        call(ramesh, post("/api/v1/portal/family/children"), staff.body("fullName", "Aarav Kulkarni", "dateOfBirth", "2016-05-01"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1].child").value(true)).andExpect(jsonPath("$.members[1].canEdit").value(true));
        long aarav = view(ramesh).at("/members/1/memberId").asLong();
        call(ramesh, put("/api/v1/portal/family/members/" + aarav), staff.body("fullName", "Aarav R. Kulkarni", "dateOfBirth", "2016-05-01"))
                .andExpect(jsonPath("$.members[1].name").value("Aarav R. Kulkarni"));

        String sunitaEmail = "sunita@" + a + ".example";
        call(ramesh, post("/api/v1/portal/family/invites"), staff.body("channel", "EMAIL", "contact", sunitaEmail, "relation", "SPOUSE"))
                .andExpect(jsonPath("$.sentInvites[0].contact").value(sunitaEmail));

        // Someone else can't take Sunita's place.
        Cookie stranger = signIn("stranger@" + a + ".example");
        long inviteId = view(signIn(sunitaEmail)).at("/invitations/0/id").asLong();
        call(stranger, post("/api/v1/portal/family/invitations/" + inviteId + "/accept"), "{}").andExpect(status().isNotFound());

        Cookie sunita = signIn(sunitaEmail);
        call(sunita, post("/api/v1/portal/family/invitations/" + inviteId + "/accept"), staff.body("yourName", "Sunita Kulkarni"))
                .andExpect(jsonPath("$.myRelation").value("SPOUSE")).andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.members.length()").value(3));
        call(sunita, post("/api/v1/portal/family/invitations/" + inviteId + "/accept"), "{}").andExpect(status().isNotFound()); // used

        // Staff see the same household on the devotee record.
        long sunitaDevotee = view(sunita).at("/members/2/devoteeId").asLong();
        mvc.perform(on(a, get("/api/v1/devotees/" + sunitaDevotee + "/household")).session(staff.loginAdmin(a)))
                .andExpect(jsonPath("$.name").value("Kulkarni family")).andExpect(jsonPath("$.members.length()").value(3));
    }

    @Test
    void adultsAreNeverEditedAsChildrenAndTheHeadIsNotRemovedByOthers() throws Exception {
        Cookie ramesh = signIn("r2@" + a + ".example");
        call(ramesh, post("/api/v1/portal/family"), staff.body("yourName", "Ramesh", "familyName", "K family"));
        String sEmail = "s2@" + a + ".example";
        call(ramesh, post("/api/v1/portal/family/invites"), staff.body("channel", "EMAIL", "contact", sEmail, "relation", "SPOUSE"));
        Cookie sunita = signIn(sEmail);
        call(sunita, post("/api/v1/portal/family/invitations/" + view(sunita).at("/invitations/0/id").asLong() + "/accept"),
                staff.body("yourName", "Sunita"));
        JsonNode fam = view(sunita);
        long head = fam.at("/members/0/memberId").asLong();
        long me = fam.at("/members/1/memberId").asLong();
        call(ramesh, put("/api/v1/portal/family/members/" + me), staff.body("fullName", "Renamed")).andExpect(status().isForbidden());
        call(sunita, delete("/api/v1/portal/family/members/" + head), null).andExpect(status().isForbidden());
        call(sunita, delete("/api/v1/portal/family/members/" + me), null).andExpect(jsonPath("$.householdId").isEmpty()); // left
        assertThat(view(ramesh).at("/members").size()).isEqualTo(1);
    }

    @Test
    void someoneInAFamilyCantJoinAnotherAndOnlyManagersAddOrInvite() throws Exception {
        Cookie x = signIn("x@" + a + ".example");
        call(x, post("/api/v1/portal/family"), staff.body("yourName", "X", "familyName", "X family"));
        Cookie y = signIn("y@" + a + ".example");
        call(y, post("/api/v1/portal/family"), staff.body("yourName", "Y", "familyName", "Y family"));
        call(y, post("/api/v1/portal/family/invites"), staff.body("channel", "EMAIL", "contact", "x@" + a + ".example", "relation", "SIBLING"));
        call(x, post("/api/v1/portal/family/invitations/" + view(x).at("/invitations/0/id").asLong() + "/accept"), "{}")
                .andExpect(status().isConflict());
        call(x, post("/api/v1/portal/family"), staff.body("familyName", "Second family")).andExpect(status().isConflict());

        Cookie nobody = signIn("nobody@" + a + ".example"); // in no household
        call(nobody, post("/api/v1/portal/family/children"), staff.body("fullName", "Kid")).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/portal/family"))).andExpect(status().isUnauthorized());
    }

    // --- helpers ------------------------------------------------------------------------------

    private JsonNode view(Cookie c) throws Exception {
        return json.readTree(mvc.perform(on(a, get("/api/v1/portal/family")).cookie(c)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(Cookie c, MockHttpServletRequestBuilder r, String body) throws Exception {
        r = on(a, r).cookie(c).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r);
    }

    private Cookie signIn(String email) throws Exception {
        String ip = "10.70." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        mvc.perform(on(a, post("/api/v1/public/devotee-login/code")).with(r -> { r.setRemoteAddr(ip); return r; })
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("channel", "EMAIL", "contact", email))).andExpect(status().isAccepted());
        String value = mvc.perform(on(a, post("/api/v1/public/devotee-login/verify")).with(r -> { r.setRemoteAddr(ip); return r; })
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("channel", "EMAIL", "contact", email, "code", senders.email.last())))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SC_DEVOTEE").getValue();
        return new Cookie("SC_DEVOTEE", value);
    }
}
