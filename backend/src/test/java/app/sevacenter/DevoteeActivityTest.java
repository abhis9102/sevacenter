package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * A devotee's activity on their record (ADR 0023): one read whose sections each follow their own
 * module's rules, a donation net that counts reversals, matches by phone and email, per trust.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DevoteeActivityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private long lakshmi;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("da-a");
        admin = staff.loginAdmin(a);
        lakshmi = id(mvc.perform(on(a, post("/api/v1/devotees")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("fullName", "Lakshmi Iyer", "phone", "9876543210",
                        "email", "lakshmi@example.org", "consentSource", "IN_PERSON"))).andExpect(status().isCreated()));
    }

    @Test
    void anAdminSeesEverythingWithANetThatCountsReversals() throws Exception {
        long kept = donation(lakshmi, "1100");
        long undone = donation(lakshmi, "500");
        donation(null, "999"); // not linked to her
        mvc.perform(on(a, post("/api/v1/donations/" + undone + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Entered twice by mistake\"}"))
                .andExpect(status().isCreated());
        long puja = id(mvc.perform(on(a, post("/api/v1/pujas")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Archana", "dakshina", "0", "active", true, "displayOrder", 1))));
        book(puja, "98765 43210", null);              // by phone
        book(puja, null, "LAKSHMI@example.org");      // by email (stored lowercased)
        book(puja, "9123456789", null);               // someone else
        mvc.perform(on(a, post("/api/v1/public/sevak")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", "Lakshmi", "email", "lakshmi@example.org", "sevaAreas", "Annadanam")))
                .andExpect(status().isCreated());

        activity(admin).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.donations.net").value("1100.00"))
                .andExpect(jsonPath("$.donations.count").value(2))
                .andExpect(jsonPath("$.donations.items[?(@.id == " + undone + ")].reversed").value(true))
                .andExpect(jsonPath("$.donations.items[?(@.id == " + kept + ")].reversed").value(false))
                .andExpect(jsonPath("$.pujaBookings.length()").value(2))
                .andExpect(jsonPath("$.eventPasses.length()").value(0))
                .andExpect(jsonPath("$.sevaOffers.length()").value(1));
    }

    @Test
    void aMemberSeesNoMoneyAndNoSevaContactsOnlyWhatTheirRoleAllows() throws Exception {
        donation(lakshmi, "1100");
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        activity(member).andExpect(status().isOk())
                .andExpect(jsonPath("$.donations").doesNotExist())
                .andExpect(jsonPath("$.sevaOffers").doesNotExist())
                .andExpect(jsonPath("$.pujaBookings").isArray())
                .andExpect(jsonPath("$.eventPasses").isArray());
    }

    @Test
    void moduleLimitsApplyToEachSectionNotJustThePath() throws Exception {
        donation(lakshmi, "1100");
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        long leaderId = staff.userId(a, leader);
        activity(leader).andExpect(jsonPath("$.donations.net").value("1100.00"));
        limit(leaderId, "{\"DONATIONS\":\"NONE\",\"PUJAS\":\"NONE\"}");
        activity(leader).andExpect(status().isOk())
                .andExpect(jsonPath("$.donations").doesNotExist())
                .andExpect(jsonPath("$.pujaBookings").doesNotExist())
                .andExpect(jsonPath("$.eventPasses").isArray());
        limit(leaderId, "{\"DONATIONS\":\"VIEW\",\"VOLUNTEERS\":\"NONE\"}");
        activity(leader).andExpect(jsonPath("$.donations.net").value("1100.00"))
                .andExpect(jsonPath("$.sevaOffers").doesNotExist());
        limit(leaderId, "{\"DEVOTEES\":\"NONE\"}");
        activity(leader).andExpect(status().isForbidden());
    }

    @Test
    void anotherTrustsDevoteeIsNotFound() throws Exception {
        String b = staff.tenant("da-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        mvc.perform(on(b, get("/api/v1/devotees/" + lakshmi + "/activity")).session(adminB)).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/devotees/" + lakshmi + "/activity"))).andExpect(status().isUnauthorized());
    }

    private ResultActions activity(MockHttpSession session) throws Exception {
        return mvc.perform(on(a, get("/api/v1/devotees/" + lakshmi + "/activity")).session(session));
    }

    private void limit(long userId, String limits) throws Exception {
        mvc.perform(on(a, put("/api/v1/users/" + userId + "/module-access")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"limits\":" + limits + "}")).andExpect(status().isOk());
    }

    private long donation(Long devoteeId, String amount) throws Exception {
        return id(mvc.perform(on(a, post("/api/v1/donations")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("devoteeId", devoteeId, "donorName", "Someone", "amount", amount, "mode", "UPI",
                        "receivedOn", LocalDate.now().toString()))).andExpect(status().isCreated()));
    }

    private void book(long puja, String phone, String email) throws Exception {
        mvc.perform(on(a, post("/api/v1/public/pujas/" + puja + "/book")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("devoteeName", "Lakshmi", "pujaDate", LocalDate.now().plusDays(2).toString(),
                        "phone", phone, "email", email))).andExpect(status().isCreated());
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }
}
