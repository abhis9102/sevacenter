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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public temple page (ADR 0017): no placeholder data, leaders edit, real service flags, per trust.
 * Darshan hours, same-day status and the aarti timetable (ADR 0024).
 */
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
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

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

    @Test
    void hoursAndTheAartiTimetableArePublishedInTimeOrder() throws Exception {
        schedule(leader, hours("05:30", "12:30", "16:00", "21:30"),
                List.of(aarti("Shej Aarti", "21:00"), aarti("Kakad Aarti", "05:30"), aarti("Madhyan Aarti", "12:00")))
                .andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.hours.morningOpen").value("05:30:00"))
                .andExpect(jsonPath("$.hours.eveningClose").value("21:30:00"))
                .andExpect(jsonPath("$.aartis.length()").value(3))
                .andExpect(jsonPath("$.aartis[0].name").value("Kakad Aarti"))
                .andExpect(jsonPath("$.aartis[2].name").value("Shej Aarti"))
                .andExpect(jsonPath("$.calendar").value("AMANTA"))
                .andExpect(jsonPath("$.status").doesNotExist());
        // Saving replaces the timetable as a whole.
        schedule(leader, hours("05:30", "12:30", null, null), List.of(aarti("Kakad Aarti", "05:30"))).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.aartis.length()").value(1))
                .andExpect(jsonPath("$.hours.eveningOpen").doesNotExist());
    }

    @Test
    void impossibleHoursAndOverlongTimetablesAreRejected() throws Exception {
        schedule(leader, hours("12:30", "05:30", null, null), List.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.hours").exists());
        schedule(leader, hours("05:30", null, null, null), List.of()).andExpect(status().isBadRequest());
        schedule(leader, hours("05:30", "13:00", "12:00", "21:00"), List.of()).andExpect(status().isBadRequest());
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            many.add(aarti("Aarti " + i, String.format("%02d:00", i + 5)));
        }
        schedule(leader, null, many).andExpect(status().isBadRequest());
        schedule(leader, null, List.of(aarti(" ", "05:30"))).andExpect(status().isBadRequest());
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.aartis.length()").value(0));
    }

    @Test
    void todaysStatusOverrideIsForLeadersAndLapsesAtMidnight() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        setStatus(member, "CLOSED", "Grahan").andExpect(status().isForbidden());
        setStatus(leader, "CLOSED", "Closed for the lunar eclipse").andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.statusNote").value("Closed for the lunar eclipse"));
        // Saving the page keeps today's status.
        save(leader).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        // The next day it no longer applies.
        tx.executeWithoutResult(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', (select id::text from tenant where slug = ?), true)",
                    String.class, a);
            jdbc.update("update temple_profile set override_on = override_on - 1");
        });
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.statusNote").doesNotExist());
        setStatus(leader, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void anotherTrustNeverSeesThisTimetableOrStatus() throws Exception {
        schedule(leader, hours("05:30", "12:30", null, null), List.of(aarti("Kakad Aarti", "05:30")));
        setStatus(leader, "CLOSED", "A only");
        String b = staff.tenant("tp-c");
        mvc.perform(on(b, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.aartis.length()").value(0))
                .andExpect(jsonPath("$.hours").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    private static Map<String, Object> hours(String mo, String mc, String eo, String ec) {
        Map<String, Object> h = new java.util.HashMap<>();
        h.put("morningOpen", mo);
        h.put("morningClose", mc);
        h.put("eveningOpen", eo);
        h.put("eveningClose", ec);
        return h;
    }

    private static Map<String, Object> aarti(String name, String at) {
        return Map.of("name", name, "at", at, "description", "Daily");
    }

    private ResultActions schedule(MockHttpSession session, Map<String, Object> hours, List<Map<String, Object>> aartis)
            throws Exception {
        return mvc.perform(on(a, put("/api/v1/temple")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("deity", "Shri Siddheshwar", "hours", hours, "aartis", aartis)));
    }

    private ResultActions setStatus(MockHttpSession session, String value, String note) throws Exception {
        return mvc.perform(on(a, put("/api/v1/temple/status")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("status", value, "note", note)));
    }

    private ResultActions save(MockHttpSession session) throws Exception {
        return mvc.perform(on(a, put("/api/v1/temple")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("deity", "Shri Siddheshwar", "address", "1 Temple Road, Pune", "helpline", "98765 43210",
                        "timings", "Darshan 5:30 am - 12:30 pm", "announcement", "Annual utsav begins on Sunday.")));
    }
}
