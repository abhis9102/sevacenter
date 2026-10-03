package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Donation history and receipts in "my seva" (ADR 0019): a verified contact sees exactly its own
 * donations, online (by the contact left at checkout) and staff-recorded (via the linked devotee),
 * and receipts only for those, with the donor PAN masked.
 */
@Import({TestcontainersConfiguration.class, OnlineDonationTest.FakeGatewayConfig.class, DevoteeLoginTest.Senders.class})
@SpringBootTest
@AutoConfigureMockMvc
class DonationHistoryTest {

    private static final String PAN = "ABCPE1234F";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private OnlineDonationTest.FakeGateway gateway;
    @Autowired
    private DevoteeLoginTest.Senders senders;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("dh-a");
        admin = staff.loginAdmin(a);
        ip = "10.31." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    @Test
    void anOnlineDonationIsFoundByTheContactLeftAtCheckout() throws Exception {
        connectGateway();
        donateOnline("501", "98765 43210", null);
        donateOnline("1100", "91234 56789", null);       // someone else
        donateOnline("251", null, null);                 // no contact left: nobody's history
        Cookie me = login(OtpChannel.SMS, "9876543210");
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(me)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.donations.length()").value(1))
                .andExpect(jsonPath("$.donations[0].amount").value("501.00"))
                .andExpect(jsonPath("$.donations[0].receiptNumber").doesNotExist());
    }

    @Test
    void aBadContactAtCheckoutIsRejectedBeforeAnyOrder() throws Exception {
        connectGateway();
        order("501", "98765", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.phone").exists());
        order("501", null, "not-an-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void aStaffRecordedDonationIsFoundThroughTheLinkedDevoteeWithAMaskedReceipt() throws Exception {
        String email = "lakshmi@" + a + ".example";
        long devotee = devotee("Lakshmi Iyer", email);
        long mine = donation(devotee);
        long other = donation(devotee("Ravi", "ravi@" + a + ".example"));
        profile();
        issue(mine).andExpect(status().isCreated());
        issue(other).andExpect(status().isCreated());

        Cookie me = login(OtpChannel.EMAIL, email.toUpperCase());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(me))
                .andExpect(jsonPath("$.donations.length()").value(1))
                .andExpect(jsonPath("$.donations[0].id").value(mine))
                .andExpect(jsonPath("$.donations[0].receiptValid").value(true))
                .andExpect(jsonPath("$.donations[0].receiptNumber").exists());
        String receipt = mvc.perform(on(a, get("/api/v1/portal/donations/" + mine + "/receipt")).cookie(me))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.donationId").value(mine))
                .andExpect(jsonPath("$.donorName").value("Lakshmi Iyer"))
                .andReturn().getResponse().getContentAsString();
        assertThat(receipt).doesNotContain(PAN).contains("1234F");
    }

    @Test
    void someoneElsesReceiptIsNotFoundAndAnonymousIsUnauthorized() throws Exception {
        long theirs = donation(devotee("Ravi", "ravi@" + a + ".example"));
        profile();
        issue(theirs).andExpect(status().isCreated());
        Cookie me = login(OtpChannel.EMAIL, "nobody@" + a + ".example");
        mvc.perform(on(a, get("/api/v1/portal/donations/" + theirs + "/receipt")).cookie(me)).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/portal/donations/" + theirs + "/receipt"))).andExpect(status().isUnauthorized());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(me)).andExpect(jsonPath("$.donations.length()").value(0));
    }

    @Test
    void aReceiptFromAnotherTrustIsNotFound() throws Exception {
        String email = "lakshmi@" + a + ".example";
        long mine = donation(devotee("Lakshmi Iyer", email));
        profile();
        issue(mine).andExpect(status().isCreated());
        String b = staff.tenant("dh-b");
        String slugA = a;
        a = b;
        Cookie onB = login(OtpChannel.EMAIL, email);
        mvc.perform(on(b, get("/api/v1/portal/donations/" + mine + "/receipt")).cookie(onB)).andExpect(status().isNotFound());
        a = slugA;
    }

    @Test
    void aReversedDonationIsMarkedItsReceiptCancelledAndTheReversalItselfIsNotListed() throws Exception {
        String email = "rev@" + a + ".example";
        long d = donation(devotee("Rev", email));
        profile();
        issue(d).andExpect(status().isCreated());
        mvc.perform(on(a, post("/api/v1/donations/" + d + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Entered twice by mistake\"}"))
                .andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/portal/me")).cookie(login(OtpChannel.EMAIL, email)))
                .andExpect(jsonPath("$.donations.length()").value(1))
                .andExpect(jsonPath("$.donations[0].reversed").value(true))
                .andExpect(jsonPath("$.donations[0].receiptNumber").exists())
                .andExpect(jsonPath("$.donations[0].receiptValid").value(false));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private Cookie login(OtpChannel channel, String contact) throws Exception {
        mvc.perform(json(post("/api/v1/public/devotee-login/code")).content(staff.body("channel", channel.name(),
                "contact", contact))).andExpect(status().isAccepted());
        String code = (channel == OtpChannel.EMAIL ? senders.email : senders.sms).last();
        String value = mvc.perform(json(post("/api/v1/public/devotee-login/verify")).content(staff.body("channel",
                        channel.name(), "contact", contact, "code", code))).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SC_DEVOTEE").getValue();
        return new Cookie("SC_DEVOTEE", value);
    }

    private void connectGateway() throws Exception {
        mvc.perform(on(a, put("/api/v1/payment-settings")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("keyId", OnlineDonationTest.KEY_ID, "keySecret", OnlineDonationTest.SECRET)))
                .andExpect(status().isOk());
    }

    private ResultActions order(String amount, String phone, String email) throws Exception {
        return mvc.perform(json(post("/api/v1/public/donations/orders"))
                .content(staff.body("amount", amount, "donorName", "Lakshmi", "phone", phone, "email", email)));
    }

    private void donateOnline(String amount, String phone, String email) throws Exception {
        String orderId = json.readTree(order(amount, phone, email).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).at("/orderId").asString();
        String paymentId = gateway.pay(orderId, Long.parseLong(amount) * 100, "upi");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(OnlineDonationTest.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(json(post("/api/v1/public/donations/confirm"))
                .content(staff.body("orderId", orderId, "paymentId", paymentId, "signature", signature)))
                .andExpect(status().isOk());
    }

    private long devotee(String name, String email) throws Exception {
        return id(mvc.perform(on(a, post("/api/v1/devotees")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("fullName", name, "email", email, "consentSource", "IN_PERSON"))));
    }

    private long donation(long devoteeId) throws Exception {
        return id(mvc.perform(on(a, post("/api/v1/donations")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("devoteeId", devoteeId, "donorName", "Lakshmi Iyer", "amount", "1100", "mode", "UPI",
                        "receivedOn", LocalDate.now().toString()))).andExpect(status().isCreated()));
    }

    private void profile() throws Exception {
        mvc.perform(on(a, put("/api/v1/trust-profile")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("legalName", "Trust " + a, "address", "1 Temple Road, Pune 411001", "pan", "AAATS1234F",
                        "registration80g", "AAATS1234FF20214", "validFrom", "2021-04-01", "validTo", "2030-03-31")))
                .andExpect(status().isOk());
    }

    private ResultActions issue(long donationId) throws Exception {
        return mvc.perform(on(a, post("/api/v1/donations/" + donationId + "/receipt")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("donorPan", PAN, "donorAddress", "12 Temple Street, Pune 411001")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r) {
        return on(a, r).with(csrf()).with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON);
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }
}
