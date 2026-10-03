package app.sevacenter.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import app.sevacenter.TestcontainersConfiguration;
import app.sevacenter.auth.RegistrationRequest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "sevacenter.dev-tokens=true")
@AutoConfigureMockMvc
class MandirAdminTest {

    private static final String PASSWORD = "password-123456";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private String tenantSlugA;
    private String tenantSlugB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setUp() throws Exception {
        tenantSlugA = "mandir-adm-a-" + UUID.randomUUID().toString().substring(0, 8);
        tenantSlugB = "mandir-adm-b-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(tenantSlugA, "Mandir Alpha", "admin@" + tenantSlugA + ".org", PASSWORD, "Admin Alpha"));
        registration.register(new RegistrationRequest(tenantSlugB, "Mandir Beta", "admin@" + tenantSlugB + ".org", PASSWORD, "Admin Beta"));

        sessionA = login(tenantSlugA, "admin@" + tenantSlugA + ".org", PASSWORD);
        sessionB = login(tenantSlugB, "admin@" + tenantSlugB + ".org", PASSWORD);
    }

    private MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder req) {
        return req.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }

    private MockHttpSession login(String slug, String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(on(slug, post("/api/v1/auth/login"))
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    @Test
    void settings_getAndPut_updatesPublicMandirCenter() throws Exception {
        // 1. Get default settings
        mvc.perform(on(tenantSlugA, get("/api/v1/mandir/settings")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandirName").value("Mandir Alpha"))
                .andExpect(jsonPath("$.aartis.length()").value(5));

        // 2. Update settings
        MandirAdminService.UpdateScheduleRequest updateReq = new MandirAdminService.UpdateScheduleRequest(
                "06:00 AM – 12:30 PM",
                "05:00 PM – 09:00 PM",
                true,
                "Bhagwan Shiva",
                "Shiva Temple Marg",
                "+91 99999 88888",
                "Kartik Shukla Purnima",
                "Rohini",
                "Grand Deepotsav Celebration this evening!",
                List.of(
                        new MandirAdminService.AartiItemDto("Morning Mangala", "06:00 AM", "First morning awakening"),
                        new MandirAdminService.AartiItemDto("Maha Deepa Aarti", "07:30 PM", "Evening divine lamps")
                )
        );

        mvc.perform(on(tenantSlugA, put("/api/v1/mandir/settings"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morningHours").value("06:00 AM – 12:30 PM"))
                .andExpect(jsonPath("$.deity").value("Bhagwan Shiva"))
                .andExpect(jsonPath("$.specialAnnouncement").value("Grand Deepotsav Celebration this evening!"))
                .andExpect(jsonPath("$.aartis.length()").value(2));

        // 3. Verify public portal reflects updated schedule
        mvc.perform(on(tenantSlugA, get("/api/v1/portal/schedule")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deity").value("Bhagwan Shiva"))
                .andExpect(jsonPath("$.specialAnnouncement").value("Grand Deepotsav Celebration this evening!"))
                .andExpect(jsonPath("$.aartis.length()").value(2));

        // 4. Verify tenant B is completely isolated
        mvc.perform(on(tenantSlugB, get("/api/v1/mandir/settings")).session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandirName").value("Mandir Beta"))
                .andExpect(jsonPath("$.deity").value("Pradhan Devata"));
    }

    @Test
    void pujas_createAndManageCatalog_priestSankalpRoster() throws Exception {
        // 1. Get existing catalog
        mvc.perform(on(tenantSlugA, get("/api/v1/mandir/pujas")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        // 2. Add custom puja
        MandirAdminService.CreatePujaRequest newPuja = new MandirAdminService.CreatePujaRequest(
                "CHANDI_HAVAN",
                "Maha Chandi Yagya",
                "Maa Durga",
                "120 mins",
                5100L,
                "Grand sacred fire ritual for victory and protection.",
                true,
                true,
                10
        );

        mvc.perform(on(tenantSlugA, post("/api/v1/mandir/pujas"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(newPuja)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CHANDI_HAVAN"))
                .andExpect(jsonPath("$.dakshinaRupees").value(5100));

        // 3. Devotee books this newly added puja with family sankalp
        DevoteePortalService.BookPujaRequest bookReq = new DevoteePortalService.BookPujaRequest(
                null,
                "CHANDI_HAVAN",
                LocalDate.now().plusDays(2),
                "08:00 AM - 10:00 AM",
                "Vikramaditya",
                "Vashishta",
                "Uttara Bhadrapada",
                "Meena",
                "Vikramaditya, Malini Devi",
                "+91 98765 00000",
                "UPI"
        );

        mvc.perform(on(tenantSlugA, post("/api/v1/portal/pujas/book"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pujaName").value("Maha Chandi Yagya"))
                .andExpect(jsonPath("$.gotra").value("Vashishta"));

        // 4. Staff/Priest views the Sankalp Roster
        String res = mvc.perform(on(tenantSlugA, get("/api/v1/mandir/pujas/bookings")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].devoteeName").value("Vikramaditya"))
                .andExpect(jsonPath("$[0].gotra").value("Vashishta"))
                .andExpect(jsonPath("$[0].familyMembers").value("Vikramaditya, Malini Devi"))
                .andReturn().getResponse().getContentAsString();

        long bookingId = json.readTree(res).get(0).get("id").asLong();

        // 5. Priest recites sankalp and marks it completed
        mvc.perform(on(tenantSlugA, post("/api/v1/mandir/pujas/bookings/" + bookingId + "/complete"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new MandirAdminController.MarkPerformedRequest("Pandit Shastri Ji"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.performedBy").value("Pandit Shastri Ji"));
    }

    @Test
    void events_createAndGateCheckIn() throws Exception {
        // 1. Create festival
        MandirAdminService.CreateEventRequest newEvent = new MandirAdminService.CreateEventRequest(
                "DEEPAWALI_2026",
                "Deepawali Maha Annakut",
                LocalDate.now().plusDays(5),
                "06:00 PM – 10:00 PM",
                "Grand 56 Bhog offering to Bhagwan Krishna and Annakut Mahaprasad.",
                "10,000 diyas illumination, fireworks",
                true,
                1000,
                true
        );

        mvc.perform(on(tenantSlugA, post("/api/v1/mandir/events"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(newEvent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DEEPAWALI_2026"));

        // 2. Devotee books Darshan pass
        DevoteePortalService.BookPassRequest passReq = new DevoteePortalService.BookPassRequest(
                null,
                "DEEPAWALI_2026",
                LocalDate.now().plusDays(5),
                "06:00 PM – 08:00 PM",
                "Devotee Rajesh",
                4,
                "+91 98111 22233"
        );

        String passRes = mvc.perform(on(tenantSlugA, post("/api/v1/portal/events/pass"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(passReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passNumber").exists())
                .andReturn().getResponse().getContentAsString();

        String passNumber = json.readTree(passRes).get("passNumber").asText();
        String qrToken = json.readTree(passRes).get("qrString").asText();

        // 3. Gate Staff checks in the pass using the scanned QR token
        mvc.perform(on(tenantSlugA, post("/api/v1/mandir/passes/check-in"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new MandirAdminController.CheckInRequest(qrToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passNumber").value(passNumber))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.checkedIn").value(true));
    }

    @Test
    void volunteers_viewAndAssignSevak() throws Exception {
        // 1. Devotee signs up as sevak
        DevoteePortalService.SevakRequest sevakReq = new DevoteePortalService.SevakRequest(
                null,
                "Suresh Sevadar",
                "+91 97777 66666",
                "Mahaprasad & Bhandara",
                "Weekends",
                "Morning",
                "Experienced in cooking bhandara"
        );

        String signupRes = mvc.perform(on(tenantSlugA, post("/api/v1/portal/volunteer"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(sevakReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long volunteerId = json.readTree(signupRes).get("id").asLong();

        // 2. Staff views volunteer list
        mvc.perform(on(tenantSlugA, get("/api/v1/mandir/volunteers")).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Suresh Sevadar"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        // 3. Staff approves and assigns sevak to Bhandara Kitchen team for festival
        MandirAdminService.VolunteerAssignmentRequest assignReq = new MandirAdminService.VolunteerAssignmentRequest(
                "ASSIGNED",
                "Kitchen Seva Team A",
                "Maha Shivratri Mahotsav"
        );

        mvc.perform(on(tenantSlugA, put("/api/v1/mandir/volunteers/" + volunteerId + "/status"))
                        .session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTeam").value("Kitchen Seva Team A"))
                .andExpect(jsonPath("$.assignedEvent").value("Maha Shivratri Mahotsav"));
    }
}
