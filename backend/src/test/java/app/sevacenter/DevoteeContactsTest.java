package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.portal.OtpChannel;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * One devotee, several verified contacts (ADR 0025): each is linked only with its own code, any of
 * them signs in to the same account, "my seva" shows what was made with any of them, and linking a
 * contact that had its own account merges it and ends that account's sessions. Plus the profile.
 */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class})
@SpringBootTest
@AutoConfigureMockMvc
class DevoteeContactsTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final String EMAIL = "lakshmi@example.org";
    private static final String PHONE = "+919822041187";

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
    private String ip;

    @BeforeEach
    void setUp() {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("dc-a");
        ip = "10.40." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        senders.email.sent.clear();
        senders.sms.sent.clear();
    }

    @Test
    void aLinkedPhoneShowsRecordsMadeWithEitherContactAndSignsInToTheSameAccount() throws Exception {
        offerSeva("Kitchen", null, EMAIL);
        offerSeva("Darshan queue", "98220 41187", null);
        Cookie session = login(OtpChannel.EMAIL, EMAIL);
        me(session).andExpect(jsonPath("$.sevakSignups.length()").value(1))
                .andExpect(jsonPath("$.contacts.length()").value(1));

        link(session, OtpChannel.SMS, PHONE).andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(2))
                .andExpect(jsonPath("$.sevakSignups.length()").value(2));

        Cookie byPhone = login(OtpChannel.SMS, PHONE);
        me(byPhone).andExpect(jsonPath("$.contacts.length()").value(2))
                .andExpect(jsonPath("$.contact").value(EMAIL))
                .andExpect(jsonPath("$.sevakSignups.length()").value(2));
    }

    @Test
    void aContactIsLinkedOnlyWithTheCodeSentToIt() throws Exception {
        offerSeva("Darshan queue", "98220 41187", null);
        Cookie session = login(OtpChannel.EMAIL, EMAIL);
        requestCode(OtpChannel.SMS, PHONE).andExpect(status().isAccepted());
        String real = senders.sms.last();
        String wrong = real.equals("000000") ? "000001" : "000000";
        mvc.perform(json(on(a, post("/api/v1/portal/contacts"))).cookie(session)
                        .content(staff.body("channel", "SMS", "contact", PHONE, "code", wrong)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_code"));
        // A code sent to the email can't vouch for the phone.
        requestCode(OtpChannel.EMAIL, EMAIL).andExpect(status().isAccepted());
        mvc.perform(json(on(a, post("/api/v1/portal/contacts"))).cookie(session)
                        .content(staff.body("channel", "SMS", "contact", PHONE, "code", senders.email.last())))
                .andExpect(status().isBadRequest());
        me(session).andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.sevakSignups.length()").value(0));
    }

    @Test
    void linkingNeedsASignedInDevoteeAndCsrf() throws Exception {
        requestCode(OtpChannel.SMS, PHONE);
        String code = senders.sms.last();
        mvc.perform(json(on(a, post("/api/v1/portal/contacts")))
                        .content(staff.body("channel", "SMS", "contact", PHONE, "code", code)))
                .andExpect(status().isUnauthorized());
        Cookie session = login(OtpChannel.EMAIL, EMAIL);
        mvc.perform(on(a, post("/api/v1/portal/contacts")).with(fromIp()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("channel", "SMS", "contact", PHONE, "code", code)))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkingAContactWithItsOwnAccountMergesItAndEndsItsSessions() throws Exception {
        Cookie phoneSession = login(OtpChannel.SMS, PHONE);
        profile(phoneSession, "fullName", "Lakshmi Iyer", "gotra", "Kashyap").andExpect(status().isOk());
        Cookie emailSession = login(OtpChannel.EMAIL, EMAIL);
        profile(emailSession, "gotra", "Bharadwaj").andExpect(status().isOk());

        link(emailSession, OtpChannel.SMS, PHONE).andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(2))
                // Gaps are filled from the merged account; the devotee's own entries win.
                .andExpect(jsonPath("$.profile.fullName").value("Lakshmi Iyer"))
                .andExpect(jsonPath("$.profile.gotra").value("Bharadwaj"));
        me(phoneSession).andExpect(status().isUnauthorized());
        me(login(OtpChannel.SMS, PHONE)).andExpect(jsonPath("$.contact").value(EMAIL))
                .andExpect(jsonPath("$.contacts.length()").value(2));
        // Linking a contact the account already has is a no-op, not an error. (Not the phone again: it
        // has had its three codes for this quarter hour.)
        link(emailSession, OtpChannel.EMAIL, EMAIL).andExpect(status().isOk()).andExpect(jsonPath("$.contacts.length()").value(2));
    }

    @Test
    void aDevoteeHasAtMostSixContacts() throws Exception {
        Cookie session = login(OtpChannel.EMAIL, EMAIL);
        for (int i = 1; i <= 5; i++) {
            link(session, OtpChannel.EMAIL, "family" + i + "@example.org").andExpect(status().isOk());
        }
        link(session, OtpChannel.EMAIL, "family6@example.org").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("too_many_contacts"));
    }

    @Test
    void theProfileIsTheDevoteesOwnAndValidated() throws Exception {
        Cookie session = login(OtpChannel.EMAIL, EMAIL);
        profile(session, "fullName", "Lakshmi Iyer", "gotra", "Kashyap", "nakshatra", "Rohini", "dateOfBirth", "1974-03-12",
                "familyNames", "Ravi, Meena", "pincode", "411030").andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.fullName").value("Lakshmi Iyer"))
                .andExpect(jsonPath("$.profile.familyNames").value("Ravi, Meena"));
        profile(session, "pincode", "12345").andExpect(status().isBadRequest());
        profile(session, "dateOfBirth", LocalDate.now().plusDays(1).toString()).andExpect(status().isBadRequest());
        profile(session, "fullName", "x".repeat(121)).andExpect(status().isBadRequest());
        me(session).andExpect(jsonPath("$.profile.gotra").value("Kashyap"));
        mvc.perform(json(on(a, put("/api/v1/portal/profile"))).content(staff.body("fullName", "Someone")))
                .andExpect(status().isUnauthorized());
        // Another trust's host doesn't know this devotee.
        String b = staff.tenant("dc-b");
        mvc.perform(on(b, get("/api/v1/portal/me")).cookie(session)).andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private void offerSeva(String areas, String phone, String email) throws Exception {
        mvc.perform(json(on(a, post("/api/v1/public/sevak")))
                        .content(staff.body("fullName", "Lakshmi", "phone", phone, "email", email, "sevaAreas", areas)))
                .andExpect(status().isCreated());
    }

    private Cookie login(OtpChannel channel, String contact) throws Exception {
        requestCode(channel, contact).andExpect(status().isAccepted());
        String code = (channel == OtpChannel.EMAIL ? senders.email : senders.sms).last();
        Cookie c = mvc.perform(json(on(a, post("/api/v1/public/devotee-login/verify")))
                        .content(staff.body("channel", channel.name(), "contact", contact, "code", code)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SC_DEVOTEE");
        assertThat(c).isNotNull();
        return new Cookie("SC_DEVOTEE", c.getValue());
    }

    private ResultActions link(Cookie session, OtpChannel channel, String contact) throws Exception {
        requestCode(channel, contact).andExpect(status().isAccepted());
        String code = (channel == OtpChannel.EMAIL ? senders.email : senders.sms).last();
        return mvc.perform(json(on(a, post("/api/v1/portal/contacts"))).cookie(session)
                .content(staff.body("channel", channel.name(), "contact", contact, "code", code)));
    }

    private ResultActions requestCode(OtpChannel channel, String contact) throws Exception {
        return mvc.perform(json(on(a, post("/api/v1/public/devotee-login/code")))
                .content(staff.body("channel", channel.name(), "contact", contact)));
    }

    private ResultActions me(Cookie session) throws Exception {
        return mvc.perform(on(a, get("/api/v1/portal/me")).cookie(session));
    }

    private ResultActions profile(Cookie session, Object... fields) throws Exception {
        return mvc.perform(json(on(a, put("/api/v1/portal/profile"))).cookie(session).content(staff.body(fields)));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r) {
        return r.with(csrf()).with(fromIp()).contentType(MediaType.APPLICATION_JSON);
    }

    private RequestPostProcessor fromIp() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }
}
