package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.donation.PaymentGateway;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Online donations into the trust's own Razorpay account (M3.3, ADR 0013), against a fake
 * gateway: a donation reaches the ledger only for a captured payment of the right order and
 * amount with a valid signature, exactly once.
 */
@Import({TestcontainersConfiguration.class, OnlineDonationTest.FakeGatewayConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class OnlineDonationTest {

    static final String KEY_ID = "rzp_test_FakeKey000001";
    static final String SECRET = "fake-gateway-secret-1";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private FakeGateway gateway;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("od-a");
        admin = staff.loginAdmin(a);
        ip = "10.11." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    // --- settings ----------------------------------------------------------------------------

    @Test
    void onlyAdminsConnectAGatewayAndTheSecretIsNeverStoredOrShownInClear() throws Exception {
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        connect(leader, KEY_ID, SECRET).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/payment-settings")).session(leader)).andExpect(status().isForbidden());

        String body = connect(admin, KEY_ID, SECRET).andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value(KEY_ID)).andExpect(jsonPath("$.live").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET);
        assertThat(mvc.perform(on(a, get("/api/v1/payment-settings")).session(admin)).andReturn().getResponse()
                .getContentAsString()).doesNotContain(SECRET);
        String stored = pinned(() -> jdbc.queryForObject("select key_secret_enc from payment_settings", String.class));
        assertThat(stored).startsWith("v1:").doesNotContain(SECRET);
    }

    @Test
    void credentialsRazorpayRejectsAreNotSaved() throws Exception {
        connect(admin, KEY_ID, "wrong-secret-value").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.keySecret").exists());
        connect(admin, "not-a-key-id", SECRET).andExpect(status().isBadRequest());
        mvc.perform(on(a, get("/api/v1/payment-settings")).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void aTrustWithoutAGatewayTakesNoOnlineDonations() throws Exception {
        order("501", "Lakshmi").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("payments_not_configured"));
        mvc.perform(on(a, get("/api/v1/public/donations/info"))).andExpect(jsonPath("$.onlineDonations").value(false));
    }

    // --- the happy path ----------------------------------------------------------------------

    @Test
    void aVerifiedPaymentBecomesOneOnlineLedgerEntry() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String orderId = orderId(order("501.50", "Lakshmi Iyer"));
        String paymentId = gateway.pay(orderId, 50150, "upi");

        JsonNode done = json.readTree(confirm(orderId, paymentId, sign(orderId, paymentId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(done.at("/amount").asString()).isEqualTo("501.50");

        Map<String, Object> row = pinned(() -> jdbc.queryForMap(
                "select channel, recorded_by, payment_ref, amount_paise, mode, donor_name from donation where id = ?",
                done.at("/donationId").asLong()));
        assertThat(row.get("channel")).isEqualTo("ONLINE");
        assertThat(row.get("recorded_by")).isNull();
        assertThat(row.get("payment_ref")).isEqualTo(paymentId);
        assertThat(row.get("amount_paise")).isEqualTo(50150L);
        assertThat(row.get("mode")).isEqualTo("UPI");
        assertThat(row.get("donor_name")).isEqualTo("Lakshmi Iyer");
    }

    // --- forged and mismatched payments ------------------------------------------------------

    @Test
    void aForgedSignatureRecordsNothing() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String orderId = orderId(order("501", "Lakshmi"));
        String paymentId = gateway.pay(orderId, 50100, "upi");
        confirm(orderId, paymentId, "0".repeat(64)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("payment_not_verified"));
        confirm(orderId, paymentId, signWith("another-secret", orderId, paymentId)).andExpect(status().isBadRequest());
        assertThat(ledgerCount()).isZero();
    }

    /** A valid signature isn't enough: the gateway's own record must match the intent exactly. */
    @Test
    void theGatewaysRecordMustMatchAmountOrderAndStatus() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String underpaid = orderId(order("501", "Lakshmi"));
        String p1 = gateway.pay(underpaid, 100, "upi");
        confirm(underpaid, p1, sign(underpaid, p1)).andExpect(status().isBadRequest());

        String notCaptured = orderId(order("501", "Lakshmi"));
        String p2 = gateway.payWithStatus(notCaptured, 50100, "upi", "authorized");
        confirm(notCaptured, p2, sign(notCaptured, p2)).andExpect(status().isBadRequest());

        String foreignCurrency = orderId(order("501", "Lakshmi"));
        String p3 = gateway.payFull(foreignCurrency, 50100, "card", "captured", "USD");
        confirm(foreignCurrency, p3, sign(foreignCurrency, p3)).andExpect(status().isBadRequest());
        assertThat(ledgerCount()).isZero();
    }

    /** One captured payment can't be replayed to settle a different (e.g. larger) order. */
    @Test
    void aPaymentCantBeReplayedOnAnotherOrder() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String small = orderId(order("1", "Lakshmi"));
        String paid = gateway.pay(small, 100, "upi");
        confirm(small, paid, sign(small, paid)).andExpect(status().isOk());
        String big = orderId(order("100000", "Lakshmi"));
        confirm(big, paid, sign(big, paid)).andExpect(status().isBadRequest());
        assertThat(ledgerCount()).isEqualTo(1);
    }

    /**
     * Same amount, different order: only the order check stops it (the amount check would pass).
     * Defence in depth for a leaked secret, where a forger could sign any (order, payment) pair.
     */
    @Test
    void aPaymentForOneOrderNeverSettlesAnotherOfTheSameAmount() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String paidOrder = orderId(order("501", "Lakshmi"));
        String otherOrder = orderId(order("501", "Lakshmi"));
        String payment = gateway.pay(paidOrder, 50100, "upi");
        confirm(otherOrder, payment, sign(otherOrder, payment)).andExpect(status().isBadRequest());
        assertThat(ledgerCount()).isZero();
    }

    @Test
    void confirmingTwiceIsIdempotent() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String orderId = orderId(order("501", "Lakshmi"));
        String paymentId = gateway.pay(orderId, 50100, "card");
        long first = json.readTree(confirm(orderId, paymentId, sign(orderId, paymentId)).andReturn().getResponse()
                .getContentAsString()).at("/donationId").asLong();
        long second = json.readTree(confirm(orderId, paymentId, sign(orderId, paymentId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/donationId").asLong();
        assertThat(second).isEqualTo(first);
        assertThat(ledgerCount()).isEqualTo(1);
    }

    @Test
    void anOrderOnlyResolvesOnItsOwnTrustsHost() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String orderId = orderId(order("501", "Lakshmi"));
        String paymentId = gateway.pay(orderId, 50100, "upi");
        String b = staff.tenant("od-b");
        mvc.perform(on(b, post("/api/v1/public/donations/confirm")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("orderId", orderId, "paymentId", paymentId, "signature", sign(orderId, paymentId))))
                .andExpect(status().isBadRequest());
        assertThat(ledgerCount()).isZero();
    }

    // --- reconciliation ----------------------------------------------------------------------

    @Test
    void reconciliationRecoversAPaymentWhoseBrowserClosed() throws Exception {
        connect(admin, KEY_ID, SECRET);
        String orderId = orderId(order("501", "Lakshmi"));
        gateway.pay(orderId, 50100, "netbanking");
        pinned(() -> jdbc.update("update payment_intent set created_at = now() - interval '10 minutes'"));

        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        mvc.perform(on(a, post("/api/v1/payment-settings/reconcile")).session(leader).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/payment-settings/reconcile")).session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settled").value(1));
        mvc.perform(on(a, post("/api/v1/payment-settings/reconcile")).session(admin).with(csrf()))
                .andExpect(jsonPath("$.settled").value(0));
        assertThat(ledgerCount()).isEqualTo(1);
        assertThat(pinned(() -> jdbc.queryForObject("select mode from donation", String.class))).isEqualTo("BANK_TRANSFER");
    }

    // --- limits and the database -------------------------------------------------------------

    @Test
    void ordersAreRateLimitedPerClientAndAmountsBounded() throws Exception {
        connect(admin, KEY_ID, SECRET);
        order("0.50", "Lakshmi").andExpect(status().isBadRequest());
        order("1000000.01", "Lakshmi").andExpect(status().isBadRequest());
        int created = 0;
        for (int i = 0; i < 25; i++) {
            if (order("1", "Lakshmi").andReturn().getResponse().getStatus() == 201) {
                created++;
            }
        }
        // Every attempt counts, the two rejected amounts included: an anonymous caller pays for each call.
        assertThat(created).isEqualTo(20 - 2);
        order("1", "Lakshmi").andExpect(status().isTooManyRequests());
    }

    /** Attribution is enforced by the database, not just the code. */
    @Test
    void everyLedgerEntryNamesItsStaffMemberOrItsPayment() {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        assertThatThrownBy(() -> pinned(() -> jdbc.update("insert into donation (tenant_id, donor_name, amount_paise, mode, "
                + "received_on, channel) values (?, 'x', 100, 'UPI', current_date, 'ONLINE')", tenantId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pinned(() -> jdbc.update("insert into donation (tenant_id, donor_name, amount_paise, mode, "
                + "received_on, channel) values (?, 'x', 100, 'UPI', current_date, 'STAFF')", tenantId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResultActions connect(MockHttpSession session, String keyId, String secret) throws Exception {
        return mvc.perform(on(a, put("/api/v1/payment-settings")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("keyId", keyId, "keySecret", secret)));
    }

    private ResultActions order(String amount, String donor) throws Exception {
        return mvc.perform(on(a, post("/api/v1/public/donations/orders")).with(csrf())
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("amount", amount, "donorName", donor)));
    }

    private String orderId(ResultActions created) throws Exception {
        return json.readTree(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/orderId").asString();
    }

    private ResultActions confirm(String orderId, String paymentId, String signature) throws Exception {
        return mvc.perform(on(a, post("/api/v1/public/donations/confirm")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("orderId", orderId, "paymentId", paymentId, "signature", signature)));
    }

    private static String sign(String orderId, String paymentId) throws Exception {
        return signWith(SECRET, orderId, paymentId);
    }

    private static String signWith(String secret, String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }

    private Integer ledgerCount() {
        return pinned(() -> jdbc.queryForObject("select count(*) from donation", Integer.class));
    }

    private <T> T pinned(java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        return tx.execute(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }

    /** An in-memory Razorpay: orders it created and payments the test "made" against them. */
    static class FakeGateway implements PaymentGateway {
        private final Map<String, Long> orders = new ConcurrentHashMap<>();
        private final Map<String, Payment> payments = new ConcurrentHashMap<>();
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public void verifyCredentials(Credentials c) {
            if (!SECRET.equals(c.keySecret())) {
                throw new GatewayException("HTTP 401");
            }
        }

        @Override
        public String createOrder(Credentials c, long amountPaise, String receipt) {
            verifyCredentials(c);
            String id = "order_fake" + seq.incrementAndGet();
            orders.put(id, amountPaise);
            return id;
        }

        @Override
        public Payment fetchPayment(Credentials c, String paymentId) {
            verifyCredentials(c);
            Payment p = payments.get(paymentId);
            if (p == null) {
                throw new GatewayException("HTTP 400");
            }
            return p;
        }

        @Override
        public List<Payment> paymentsOfOrder(Credentials c, String orderId) {
            verifyCredentials(c);
            List<Payment> out = new ArrayList<>();
            payments.values().stream().filter(p -> p.orderId().equals(orderId)).forEach(out::add);
            return out;
        }

        String pay(String orderId, long paise, String method) {
            return payFull(orderId, paise, method, "captured", "INR");
        }

        String payWithStatus(String orderId, long paise, String method, String status) {
            return payFull(orderId, paise, method, status, "INR");
        }

        String payFull(String orderId, long paise, String method, String status, String currency) {
            String id = "pay_fake" + seq.incrementAndGet();
            payments.put(id, new Payment(id, orderId, status, paise, currency, method));
            return id;
        }
    }

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        FakeGateway fakeGateway() {
            return new FakeGateway();
        }
    }
}
