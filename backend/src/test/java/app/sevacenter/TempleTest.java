package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

/** The public temple page (ADR 0017): no placeholder data, leaders edit, real service flags, per trust. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TempleTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("tp-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
    }

    @Test
    void anUnconfiguredTempleShowsItsNameAndNothingMadeUp() throws Exception {
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.trustName").value("Trust " + a))
                .andExpect(jsonPath("$.timings").doesNotExist())
                .andExpect(jsonPath("$.helpline").doesNotExist())
                .andExpect(jsonPath("$.onlineDonations").value(false))
                .andExpect(jsonPath("$.upcomingEvents").value(false))
                .andExpect(jsonPath("$.pujaBooking").value(false));
    }

    @Test
    void leadersEditThePageAndTheWorldSeesIt() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        save(member).andExpect(status().isForbidden());
        save(leader).andExpect(status().isOk()).andExpect(jsonPath("$.helpline").value("+919876543210"));
        mvc.perform(on(a, get("/api/v1/temple")).session(member)).andExpect(jsonPath("$.deity").value("Shri Siddheshwar"));
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.timings").value("Darshan 5:30 am - 12:30 pm"))
                .andExpect(jsonPath("$.announcement").value("Annual utsav begins on Sunday."));
    }

    @Test
    void serviceLinksAppearOnlyWhenTheServiceExists() throws Exception {
        long id = json.readTree(mvc.perform(on(a, post("/api/v1/events")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("title", "Utsav",
                        "startsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).toString(),
                        "endsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(2).toString(),
                        "capacity", 100, "registrationOpen", true))).andReturn().getResponse().getContentAsString()).at("/id").asLong();
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.upcomingEvents").value(false));
        mvc.perform(on(a, post("/api/v1/events/" + id + "/publish")).session(leader).with(csrf())).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.upcomingEvents").value(true));
        mvc.perform(on(a, post("/api/v1/pujas")).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Archana", "dakshina", "0", "active", true, "displayOrder", 1)));
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.pujaBooking").value(true));
    }

    @Test
    void eachTrustShowsOnlyItsOwnPage() throws Exception {
        save(leader);
        String b = staff.tenant("tp-b");
        mvc.perform(on(b, get("/api/v1/public/temple"))).andExpect(jsonPath("$.trustName").value("Trust " + b))
                .andExpect(jsonPath("$.announcement").doesNotExist());
        mvc.perform(get("/api/v1/public/temple").with(r -> { r.setServerName("unknown.sevacenter.app"); return r; }))
                .andExpect(status().isNotFound());
    }

    private ResultActions save(MockHttpSession session) throws Exception {
        return mvc.perform(on(a, put("/api/v1/temple")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("deity", "Shri Siddheshwar", "address", "1 Temple Road, Pune", "helpline", "98765 43210",
                        "timings", "Darshan 5:30 am - 12:30 pm", "announcement", "Annual utsav begins on Sunday.")));
    }
}
