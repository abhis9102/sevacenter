package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.portal.OtpSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Devotee login (ADR 0018): codes only ever reach the contact, wrong guesses are capped, sending
 * is limited, an account exists only after verification, and the devotee cookie and the staff
 * session open strictly separate APIs on strictly their own trust.
 */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class})
@SpringBootTest
@AutoConfigureMockMvc
class DevoteeLoginTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private PostgreSQLContainer postgres;
    @Autowired
    private Senders senders;

    private TestStaff staff;
    private String a;
    private String ip;

    @BeforeEach
    void setUp() {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("dl-a");
        ip = "10.30." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        senders.email.sent.clear();
        senders.sms.sent.clear();
    }

    @Test
    void anEmailedCodeLogsInAndShowsOnlyThatContactsSeva() throws Exception {
        String me = "lakshmi@" + a + ".example";
        long puja = createFreePuja();
        book(puja, "Lakshmi@" + a + ".example");             // stored lowercased: still hers
        book(puja, "someone-else@" + a + ".example");
        mvc.perform(on(a, post("/api/v1/public/sevak")).with(csrf()).with(fromIp()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", "Lakshmi", "email", me, "sevaAreas", "Annadanam"))).andExpect(status().isCreated());

        Cookie session = login(OtpChannel.EMAIL, " " + me.toUpperCase() + " ");
        assertThat(senders.email.sent).singleElement().satisfies(s -> {
            assertThat(s.to()).isEqualTo(me);
            assertThat(s.trustName()).isEqualTo("Trust " + a);
        });

        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(me))
                .andExpect(jsonPath("$.pujaBookings.length()").value(1))
                .andExpect(jsonPath("$.pujaBookings[0].pujaName").value("Archana"))
                .andExpect(jsonPath("$.sevakSignups.length()").value(1))
                .andExpect(jsonPath("$.eventPasses.length()").value(0));
    }

    @Test
    void theCookieIsHttpOnlyAndScopedToThePortal() throws Exception {
        String to = "c@" + a + ".example";
        requestCode(OtpChannel.EMAIL, to).andExpect(status().isAccepted());
        MockHttpServletResponse r = verify(OtpChannel.EMAIL, to, senders.email.last()).andExpect(status().isOk())
                .andReturn().getResponse();
        String header = r.getHeader("Set-Cookie");
        assertThat(header).startsWith("SC_DEVOTEE=").contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/portal");
        assertThat(r.getContentAsString()).doesNotContain(senders.email.last());
    }

    @Test
    void theCodeIsNeverInAResponseAndEveryContactGetsTheSameAnswer() throws Exception {
        String first = requestCode(OtpChannel.EMAIL, "new@" + a + ".example").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        login(OtpChannel.EMAIL, "known@" + a + ".example");
        String known = requestCode(OtpChannel.EMAIL, "known@" + a + ".example").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(known).isEqualTo("{\"sent\":true}");
    }

    @Test
    void noAccountExistsUntilACodeIsVerified() throws Exception {
        String to = "pending@" + a + ".example";
        requestCode(OtpChannel.EMAIL, to).andExpect(status().isAccepted());
        assertThat(accounts(to)).isZero();
        verify(OtpChannel.EMAIL, to, wrong(senders.email.last())).andExpect(status().isBadRequest());
        assertThat(accounts(to)).isZero();
        verify(OtpChannel.EMAIL, to, senders.email.last()).andExpect(status().isOk());
        assertThat(accounts(to)).isOne();
    }

    @Test
    void fiveWrongGuessesBurnTheCode() throws Exception {
        String to = "guess@" + a + ".example";
        requestCode(OtpChannel.EMAIL, to);
        String code = senders.email.last();
        for (int i = 0; i < 5; i++) {
            verify(OtpChannel.EMAIL, to, wrong(code)).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"invalid_code\"}", true));
        }
        verify(OtpChannel.EMAIL, to, code).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"invalid_code\"}", true));
    }

    @Test
    void aCodeWorksOnceAndANewCodeReplacesTheOldOne() throws Exception {
        String to = "once@" + a + ".example";
        requestCode(OtpChannel.EMAIL, to);
        String old = senders.email.last();
        requestCode(OtpChannel.EMAIL, to);
        String current = senders.email.last();
        if (!old.equals(current)) { // 1 in a million they're equal
            verify(OtpChannel.EMAIL, to, old).andExpect(status().isBadRequest());
        }
        verify(OtpChannel.EMAIL, to, current).andExpect(status().isOk());
        verify(OtpChannel.EMAIL, to, current).andExpect(status().isBadRequest());
    }

    @Test
    void anExpiredCodeIsRefused() throws Exception {
        String to = "late@" + a + ".example";
        requestCode(OtpChannel.EMAIL, to);
        try (Connection c = postgres.createConnection("");
             PreparedStatement s = c.prepareStatement("update devotee_otp set expires_at = now() - interval '1 second' "
                     + "where tenant_id = (select id from tenant where slug = ?)")) {
            s.setString(1, a);
            assertThat(s.executeUpdate()).isOne();
        }
        verify(OtpChannel.EMAIL, to, senders.email.last()).andExpect(status().isBadRequest());
    }

    @Test
    void aCodeOnlyWorksForTheContactItWasSentTo() throws Exception {
        requestCode(OtpChannel.EMAIL, "x@" + a + ".example");
        requestCode(OtpChannel.EMAIL, "y@" + a + ".example");
        verify(OtpChannel.EMAIL, "y@" + a + ".example", senders.email.sent.get(0).code()).andExpect(status().isBadRequest());
    }

    @Test
    void sendingIsLimitedPerContact() throws Exception {
        String to = "flood@" + a + ".example";
        for (int i = 0; i < 3; i++) {
            requestCode(OtpChannel.EMAIL, to).andExpect(status().isAccepted());
        }
        requestCode(OtpChannel.EMAIL, to).andExpect(status().isTooManyRequests());
        assertThat(senders.email.sent).hasSize(3);
    }

    @Test
    void smsGoesOnlyToIndianMobiles() throws Exception {
        requestCode(OtpChannel.SMS, "+14155550100").andExpect(status().isBadRequest());
        requestCode(OtpChannel.SMS, "+911123456789").andExpect(status().isBadRequest()); // landline
        assertThat(senders.sms.sent).isEmpty();
        Cookie session = login(OtpChannel.SMS, "098765 43210");
        assertThat(senders.sms.sent).singleElement().satisfies(s -> assertThat(s.to()).isEqualTo("+919876543210"));
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(session)).andExpect(jsonPath("$.contact").value("+919876543210"));
    }

    @Test
    void anUnconfiguredChannelIsNotOffered() throws Exception {
        mvc.perform(on(a, get("/api/v1/public/devotee-login"))).andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.sms").value(true));
        senders.sms.available = false;
        try {
            mvc.perform(on(a, get("/api/v1/public/devotee-login"))).andExpect(jsonPath("$.sms").value(false));
            requestCode(OtpChannel.SMS, "9876543210").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("channel_unavailable"));
        } finally {
            senders.sms.available = true;
        }
    }

    @Test
    void aProviderFailureLeavesNoUsableCode() throws Exception {
        String to = "down@" + a + ".example";
        senders.email.failing = true;
        try {
            requestCode(OtpChannel.EMAIL, to).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("delivery_failed"));
        } finally {
            senders.email.failing = false;
        }
        verify(OtpChannel.EMAIL, to, senders.email.last()).andExpect(status().isBadRequest());
    }

    @Test
    void devoteeAndStaffAccessNeverMix() throws Exception {
        Cookie devotee = login(OtpChannel.EMAIL, "mix@" + a + ".example");
        mvc.perform(on(a, get("/api/v1/devotees")).cookie(devotee)).andExpect(status().isUnauthorized());
        mvc.perform(on(a, get("/api/v1/me")).cookie(devotee)).andExpect(status().isUnauthorized());
        MockHttpSession admin = staff.loginAdmin(a);
        mvc.perform(on(a, get("/api/v1/portal/me")).session(admin)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"not_logged_in\"}", true));
    }

    @Test
    void aSessionWorksOnlyOnItsOwnTrustAndEndsAtLogout() throws Exception {
        Cookie session = login(OtpChannel.EMAIL, "home@" + a + ".example");
        String b = staff.tenant("dl-b");
        mvc.perform(on(b, get("/api/v1/portal/me")).cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(session)).andExpect(status().isOk());
        mvc.perform(on(a, post("/api/v1/portal/logout")).cookie(session)).andExpect(status().isForbidden()); // CSRF
        mvc.perform(on(a, post("/api/v1/portal/logout")).cookie(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(new Cookie("SC_DEVOTEE", "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private Cookie login(OtpChannel channel, String contact) throws Exception {
        requestCode(channel, contact).andExpect(status().isAccepted());
        String code = (channel == OtpChannel.EMAIL ? senders.email : senders.sms).last();
        Cookie c = verify(channel, contact, code).andExpect(status().isOk()).andReturn().getResponse().getCookie("SC_DEVOTEE");
        assertThat(c).isNotNull();
        return new Cookie("SC_DEVOTEE", c.getValue());
    }

    private ResultActions requestCode(OtpChannel channel, String contact) throws Exception {
        return mvc.perform(publicJson(on(a, post("/api/v1/public/devotee-login/code")))
                .content(staff.body("channel", channel.name(), "contact", contact)));
    }

    private ResultActions verify(OtpChannel channel, String contact, String code) throws Exception {
        return mvc.perform(publicJson(on(a, post("/api/v1/public/devotee-login/verify")))
                .content(staff.body("channel", channel.name(), "contact", contact, "code", code)));
    }

    private MockHttpServletRequestBuilder publicJson(MockHttpServletRequestBuilder r) {
        return r.with(csrf()).with(fromIp()).contentType(MediaType.APPLICATION_JSON);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor fromIp() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String wrong(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    private long createFreePuja() throws Exception {
        MockHttpSession leader = staff.loginAdmin(a);
        return json.readTree(mvc.perform(on(a, post("/api/v1/pujas")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Archana", "dakshina", "0", "active", true, "displayOrder", 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private void book(long puja, String email) throws Exception {
        mvc.perform(publicJson(on(a, post("/api/v1/public/pujas/" + puja + "/book")))
                .content(staff.body("devoteeName", "Lakshmi", "pujaDate", LocalDate.now().plusDays(2).toString(),
                        "email", email))).andExpect(status().isCreated());
    }

    private long accounts(String contact) throws Exception {
        try (Connection c = postgres.createConnection("");
             PreparedStatement s = c.prepareStatement("select count(*) from devotee_account where contact = ?")) {
            s.setString(1, contact);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    // --- recording senders: what would have been delivered, in place of SMTP and MSG91 ------------

    record Sent(String to, String code, String trustName) { }

    static class Recorder implements OtpSender {
        final OtpChannel channel;
        final List<Sent> sent = new ArrayList<>();
        volatile boolean available = true;
        volatile boolean failing;

        Recorder(OtpChannel channel) {
            this.channel = channel;
        }

        @Override
        public OtpChannel channel() {
            return channel;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public synchronized void send(String to, String code, String trustName) {
            if (failing) {
                throw new IllegalStateException("provider down");
            }
            sent.add(new Sent(to, code, trustName));
        }

        synchronized String last() {
            return sent.isEmpty() ? "000000" : sent.get(sent.size() - 1).code();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Senders {
        final Recorder email = new Recorder(OtpChannel.EMAIL);
        final Recorder sms = new Recorder(OtpChannel.SMS);

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        OtpSender recordingEmail() {
            return email;
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        OtpSender recordingSms() {
            return sms;
        }
    }
}
