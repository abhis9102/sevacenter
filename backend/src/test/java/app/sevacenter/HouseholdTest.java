package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import tools.jackson.databind.json.JsonMapper;

/** Households (ADR 0038): a family of devotee records, staff-run, visible to the family itself. */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class})
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1", "sevacenter.mail.from=no-reply@test.example"})
@AutoConfigureMockMvc
class HouseholdTest {

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
        a = staff.tenant("hh-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aFamilyIsBuiltFromDevoteeRecordsWithOneHeadAndOneHouseholdEach() throws Exception {
        long ramesh = devotee("Ramesh Kulkarni", "ramesh@" + a + ".example");
        long sunita = devotee("Sunita Kulkarni", null);
        long aarav = devotee("Aarav Kulkarni", null);
        long h = household("Kulkarni family", ramesh);
        add(h, sunita, "SPOUSE").andExpect(status().isCreated());
        add(h, aarav, "CHILD").andExpect(status().isCreated());

        mvc.perform(on(a, get("/api/v1/devotees/" + aarav + "/household")).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kulkarni family"))
                .andExpect(jsonPath("$.gotra").value("Kashyap"))
                .andExpect(jsonPath("$.addressLine").value("12 Temple Street"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].relation").value("HEAD"));

        add(h, devotee("Someone Else", null), "HEAD").andExpect(status().isBadRequest()); // one head only
        long other = household("Another family", devotee("Meera Joshi", null));
        add(other, aarav, "CHILD").andExpect(status().isConflict());                    // one household each
    }

    @Test
    void membersSeeNamesButNotTheAddressOrTheFamilysGiving() throws Exception {
        long h = household("Kulkarni family", devotee("Ramesh Kulkarni", null));
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(on(a, get("/api/v1/households/" + h)).session(member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].fullName").value("Ramesh Kulkarni"))
                .andExpect(jsonPath("$.addressLine").isEmpty())
                .andExpect(jsonPath("$.givingThisYear").isEmpty())
                .andExpect(jsonPath("$.masked").value(true));
        mvc.perform(on(a, post("/api/v1/households/" + h + "/members")).session(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("devoteeId", 1, "relation", "CHILD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theFamilysGivingIsTheNetOfItsMembersThisYear() throws Exception {
        long ramesh = devotee("Ramesh Kulkarni", null);
        long sunita = devotee("Sunita Kulkarni", null);
        long h = household("Kulkarni family", ramesh);
        add(h, sunita, "SPOUSE");
        donate(ramesh, "1001.00");
        long reversed = donate(sunita, "500.00");
        donate(sunita, "251.00");
        mvc.perform(on(a, post("/api/v1/donations/" + reversed + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("reason", "Entered twice by mistake")))
                .andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/households/" + h)).session(admin))
                .andExpect(jsonPath("$.givingThisYear").value("1252.00"));
    }

    @Test
    void aDevoteeSeesTheirOwnFamilyNamesAndRelationsOnly() throws Exception {
        String email = "ramesh@" + a + ".example";
        long ramesh = devotee("Ramesh Kulkarni", email);
        long sunita = devotee("Sunita Kulkarni", "sunita-private@" + a + ".example");
        long h = household("Kulkarni family", ramesh);
        add(h, sunita, "SPOUSE");

        Cookie me = signIn(email);
        String body = mvc.perform(on(a, get("/api/v1/portal/household")).cookie(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kulkarni family"))
                .andExpect(jsonPath("$.members[1].name").value("Sunita Kulkarni"))
                .andExpect(jsonPath("$.members[1].relation").value("SPOUSE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("sunita-private").doesNotContain("Temple Street");

        mvc.perform(on(a, get("/api/v1/portal/household")).cookie(signIn("nobody@" + a + ".example")))
                .andExpect(status().isNoContent());
    }

    @Test
    void erasingADevoteeTakesThemOutOfTheirHousehold() throws Exception {
        long ramesh = devotee("Ramesh Kulkarni", null);
        long sunita = devotee("Sunita Kulkarni", null);
        long h = household("Kulkarni family", ramesh);
        add(h, sunita, "SPOUSE");
        donate(sunita, "101.00"); // ledger rows: anonymised, not deleted
        mvc.perform(on(a, delete("/api/v1/devotees/" + sunita)).session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/households/" + h)).session(admin)).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void anotherTrustsHouseholdIsInvisible() throws Exception {
        long h = household("Kulkarni family", devotee("Ramesh Kulkarni", null));
        String b = staff.tenant("hh-b");
        mvc.perform(on(b, get("/api/v1/households/" + h)).session(staff.loginAdmin(b))).andExpect(status().isNotFound());
    }

    // --- helpers ------------------------------------------------------------------------------

    private long devotee(String name, String email) throws Exception {
        return json.readTree(mvc.perform(on(a, post("/api/v1/devotees")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("fullName", name, "email", email, "consentSource", "IN_PERSON")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private long household(String name, long head) throws Exception {
        String body = "{\"household\":" + staff.body("name", name, "gotra", "Kashyap", "addressLine", "12 Temple Street",
                "city", "Pune", "pincode", "411001") + ",\"devoteeId\":" + head + ",\"relation\":\"HEAD\"}";
        return json.readTree(mvc.perform(on(a, post("/api/v1/households")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private ResultActions add(long household, long devotee, String relation) throws Exception {
        return mvc.perform(on(a, post("/api/v1/households/" + household + "/members")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("devoteeId", devotee, "relation", relation)));
    }

    private long donate(long devotee, String amount) throws Exception {
        return json.readTree(mvc.perform(on(a, post("/api/v1/donations")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(staff.body("devoteeId", devotee, "amount", amount,
                                "mode", "UPI", "reference", "UPI123", "receivedOn", LocalDate.now().toString())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private Cookie signIn(String email) throws Exception {
        String ip = "10.60." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
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
