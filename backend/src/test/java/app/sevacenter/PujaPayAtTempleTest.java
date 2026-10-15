package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

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

/** Book online, pay the dakshina at the temple: reserved until staff collect it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PujaPayAtTempleTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("pat-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aPayAtTempleBookingIsReservedUntilStaffCollectTheDakshina() throws Exception {
        long puja = puja("Rudrabhishekam", "1100.00");
        // Online payments aren't set up for this trust: paying online fails, paying at the temple works.
        book(puja, false).andExpect(status().is4xxClientError());
        book(puja, true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAY_AT_TEMPLE")).andExpect(jsonPath("$.orderId").isEmpty());

        long id = json.readTree(mvc.perform(on(a, get("/api/v1/puja-bookings")).param("date", date().toString()).session(admin))
                .andExpect(jsonPath("$[0].status").value("PAY_AT_TEMPLE"))
                .andReturn().getResponse().getContentAsString()).at("/0/id").asLong();

        mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/performed")).session(admin).with(csrf()))
                .andExpect(status().isConflict()); // not before the dakshina is collected
        collect(id, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.counterMode").value("CASH"));
        collect(id, admin).andExpect(status().isConflict()); // once only
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/performed")).session(admin).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void onlyLeadersCollectMoneyAndAFreePujaNeedsNoPayment() throws Exception {
        long paid = puja("Satyanarayan", "751.00");
        book(paid, true).andExpect(status().isCreated());
        long id = json.readTree(mvc.perform(on(a, get("/api/v1/puja-bookings")).param("date", date().toString()).session(admin))
                .andReturn().getResponse().getContentAsString()).at("/0/id").asLong();
        collect(id, staff.staff(a, admin, "MEMBER")).andExpect(status().isForbidden());

        long free = puja("Archana", "0");
        book(free, true).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private long puja(String name, String dakshina) throws Exception {
        return json.readTree(mvc.perform(on(a, post("/api/v1/pujas")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("name", name, "dakshina", dakshina, "active", true, "displayOrder", 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private ResultActions book(long puja, boolean atTemple) throws Exception {
        String ip = "10.80.0." + NEXT_IP.getAndIncrement();
        return mvc.perform(on(a, post("/api/v1/public/pujas/" + puja + "/book")).with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("devoteeName", "Lakshmi", "pujaDate", date().toString(), "phone", "9876543210",
                        "payAtTemple", atTemple)));
    }

    private ResultActions collect(long id, MockHttpSession session) throws Exception {
        return mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/collect")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("mode", "CASH")));
    }

    private static LocalDate date() {
        return LocalDate.now().plusDays(3);
    }
}
