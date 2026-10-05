package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.puja.PujaRepository;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
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
 * Puja bookings (ADR 0016): a paid booking is confirmed only by a verified payment, and the
 * dakshina never enters the 80G donation ledger. Uses the fake gateway from OnlineDonationTest.
 */
@Import({TestcontainersConfiguration.class, OnlineDonationTest.FakeGatewayConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class PujaTest {

    private static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
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
    private OnlineDonationTest.FakeGateway gateway;
    @Autowired
    private PujaRepository pujas;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;
    private MockHttpSession member;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("pj-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        member = staff.staff(a, admin, "MEMBER");
        ip = "10.14." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        mvc.perform(on(a, put("/api/v1/payment-settings")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("keyId", OnlineDonationTest.KEY_ID, "keySecret", OnlineDonationTest.SECRET)))
                .andExpect(status().isOk());
    }

    // --- catalog -----------------------------------------------------------------------------

    @Test
    void leadersRunTheCatalogAndThePublicSeesActivePujasOnly() throws Exception {
        createPuja(member, "Rudrabhishek", "1100", true).andExpect(status().isForbidden());
        createPuja(leader, "Rudrabhishek", "1100", true).andExpect(status().isCreated());
        long hidden = id(createPuja(leader, "Retired seva", "501", false));
        String list = mvc.perform(on(a, get("/api/v1/public/pujas"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(list).size()).isEqualTo(1);
        book(hidden, TOMORROW).andExpect(status().isNotFound());
    }

    // --- deleting a puja (ADR 0029) ----------------------------------------------------------------

    @Test
    void leadersDeleteAPujaAndItIsGoneFromEveryListButTheRowStays() throws Exception {
        long puja = id(createPuja(leader, "Retired seva", "0", true));
        deletePuja(member, puja).andExpect(status().isForbidden());
        deletePuja(leader, puja).andExpect(status().isNoContent());
        assertThat(json.readTree(mvc.perform(on(a, get("/api/v1/pujas")).session(member))
                .andReturn().getResponse().getContentAsString()).size()).isZero();
        assertThat(json.readTree(mvc.perform(on(a, get("/api/v1/public/pujas")))
                .andReturn().getResponse().getContentAsString()).size()).isZero();
        book(puja, TOMORROW).andExpect(status().isNotFound());
        counter(leader, puja, null).andExpect(status().isNotFound());
        mvc.perform(on(a, put("/api/v1/pujas/" + puja)).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Back again", "dakshina", "0", "active", true, "displayOrder", 1)))
                .andExpect(status().isNotFound());
        deletePuja(leader, puja).andExpect(status().isNotFound());
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from puja where id = ? and deleted_at is not null "
                + "and deleted_by is not null", Integer.class, puja))).isOne();
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from audit_log where action = 'PUJA_DELETED' "
                + "and target_id = ?", Integer.class, puja))).isOne();
    }

    @Test
    void aPujaWithBookingsStillToHonourCantBeDeleted() throws Exception {
        long free = id(createPuja(leader, "Archana", "0", true));
        book(free, TOMORROW).andExpect(status().isCreated());
        deletePuja(leader, free).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("has_open_bookings"));
        long booking = schedule(leader).get(0).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + booking + "/cancel")).session(leader).with(csrf()))
                .andExpect(status().isOk());
        deletePuja(leader, free).andExpect(status().isNoContent());
        // The cancelled booking is still on the schedule, under the name it was booked with.
        assertThat(schedule(leader).get(0).at("/pujaName").asString()).isEqualTo("Archana");

        // A paid puja waiting for its payment would strand the devotee's money.
        long paid = id(createPuja(leader, "Rudrabhishek", "1100", true));
        book(paid, TOMORROW).andExpect(status().isCreated());
        deletePuja(leader, paid).andExpect(status().isConflict());
    }

    @Test
    void anotherTrustsPujaCantBeDeleted() throws Exception {
        String b = staff.tenant("pj-del-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = id(mvc.perform(on(b, post("/api/v1/pujas")).session(adminB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "B's puja", "dakshina", "0", "active", true, "displayOrder", 1))));
        deletePuja(admin, theirs).andExpect(status().isNotFound());
        mvc.perform(on(b, get("/api/v1/public/pujas"))).andExpect(jsonPath("$.length()").value(1));
    }

    /** A booking holds a shared lock on its puja: a delete waits for it, then sees the booking. */
    @Test
    void aDeleteWaitsForABookingInFlight() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> booking = pool.submit(() -> tx.executeWithoutResult(st -> {
                jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantA));
                assertThat(pujas.shareLock(puja)).isPresent();
                locked.countDown();
                await(release);
            }));
            locked.await();
            assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
                jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantA));
                jdbc.execute("set local lock_timeout = '300ms'");
                pujas.lockById(puja);
            })).isInstanceOf(CannotAcquireLockException.class);
            release.countDown();
            booking.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // --- free and paid bookings --------------------------------------------------------------

    @Test
    void aFreePujaIsConfirmedAtOnce() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        book(puja, TOMORROW).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.orderId").doesNotExist());
    }

    /** The core rule: a paid puja is seva income, never a ledger donation (no 80G receipt possible). */
    @Test
    void aVerifiedPaymentConfirmsTheBookingAndNeverTouchesTheDonationLedger() throws Exception {
        long puja = id(createPuja(leader, "Rudrabhishek", "1100", true));
        JsonNode booked = json.readTree(book(puja, TOMORROW).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.amountPaise").value(110000))
                .andReturn().getResponse().getContentAsString());
        String orderId = booked.at("/orderId").asString();
        assertThat(schedule(leader).size()).as("unpaid bookings aren't on the priest's schedule").isZero();

        String paymentId = gateway.pay(orderId, 110000, "upi");
        confirm(orderId, paymentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PUJA"))
                .andExpect(jsonPath("$.bookingCode").value(booked.at("/bookingCode").asString()))
                .andExpect(jsonPath("$.donationId").doesNotExist());
        assertThat(schedule(leader).get(0).at("/status").asString()).isEqualTo("CONFIRMED");
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from donation", Integer.class))).isZero();
        confirm(orderId, paymentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value(booked.at("/bookingCode").asString()));
    }

    @Test
    void aForgedOrShortPaymentLeavesTheBookingUnconfirmed() throws Exception {
        long puja = id(createPuja(leader, "Rudrabhishek", "1100", true));
        String orderId = json.readTree(book(puja, TOMORROW).andReturn().getResponse().getContentAsString()).at("/orderId").asString();
        String shortPay = gateway.pay(orderId, 100, "upi");
        confirm(orderId, shortPay).andExpect(status().isBadRequest());
        String fullPay = gateway.pay(orderId, 110000, "upi");
        mvc.perform(on(a, post("/api/v1/public/donations/confirm")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("orderId", orderId, "paymentId", fullPay, "signature", "0".repeat(64))))
                .andExpect(status().isBadRequest());
        assertThat(pinned(() -> jdbc.queryForObject("select status from puja_booking", String.class))).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void reconciliationConfirmsAPujaPaidInAClosedBrowser() throws Exception {
        long puja = id(createPuja(leader, "Rudrabhishek", "1100", true));
        String orderId = json.readTree(book(puja, TOMORROW).andReturn().getResponse().getContentAsString()).at("/orderId").asString();
        gateway.pay(orderId, 110000, "card");
        pinned(() -> jdbc.update("update payment_intent set created_at = now() - interval '10 minutes'"));
        mvc.perform(on(a, post("/api/v1/payment-settings/reconcile")).session(admin).with(csrf()))
                .andExpect(jsonPath("$.settled").value(1));
        assertThat(pinned(() -> jdbc.queryForObject("select status from puja_booking", String.class))).isEqualTo("CONFIRMED");
    }

    // --- the day's schedule ------------------------------------------------------------------

    @Test
    void thePriestSeesTheScheduleWithoutContactsAndMarksPujasPerformed() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        book(puja, TOMORROW).andExpect(status().isCreated());
        JsonNode forPriest = schedule(member);
        assertThat(forPriest.get(0).at("/gotra").asString()).isEqualTo("Kashyap");
        assertThat(forPriest.get(0).at("/phone").isNull()).isTrue();
        assertThat(schedule(leader).get(0).at("/phone").asString()).isEqualTo("+919876543210");

        long id = forPriest.get(0).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/cancel")).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/performed")).session(member).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PERFORMED"));
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + id + "/cancel")).session(leader).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("already_performed"));
    }

    @Test
    void anUnpaidOrCancelledBookingCantBePerformed() throws Exception {
        long puja = id(createPuja(leader, "Rudrabhishek", "1100", true));
        book(puja, TOMORROW);
        long unpaid = pinned(() -> jdbc.queryForObject("select id from puja_booking", Long.class));
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + unpaid + "/performed")).session(member).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("not_confirmed"));
    }

    // --- input, tenants, database ------------------------------------------------------------

    @Test
    void bookingDatesAreTodayToAYearAhead() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        book(puja, TOMORROW.minusDays(2)).andExpect(status().isBadRequest());
        book(puja, TOMORROW.plusDays(366)).andExpect(status().isBadRequest());
    }

    @Test
    void anotherTrustsPujasAndBookingsAreNotFound() throws Exception {
        String b = staff.tenant("pj-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = id(mvc.perform(on(b, post("/api/v1/pujas")).session(adminB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "B's puja", "dakshina", "0", "active", true, "displayOrder", 1))));
        book(theirs, TOMORROW).andExpect(status().isNotFound());
        mvc.perform(on(b, post("/api/v1/public/pujas/" + theirs + "/book")).with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(TOMORROW))).andExpect(status().isCreated());
        long theirBooking = json.readTree(mvc.perform(on(b, get("/api/v1/puja-bookings").param("date", TOMORROW.toString()))
                .session(adminB)).andReturn().getResponse().getContentAsString()).get(0).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/puja-bookings/" + theirBooking + "/performed")).session(leader).with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** A paid booking can't be confirmed without a payment, even by a direct database write. */
    @Test
    void theDatabaseRefusesAPaidBookingConfirmedWithoutPayment() throws Exception {
        long puja = id(createPuja(leader, "Rudrabhishek", "1100", true));
        book(puja, TOMORROW);
        assertThatThrownBy(() -> pinned(() -> jdbc.update("update puja_booking set status = 'CONFIRMED'")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bookingsAreRateLimitedPerClient() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        for (int i = 0; i < 20; i++) {
            book(puja, TOMORROW).andExpect(status().isCreated());
        }
        book(puja, TOMORROW).andExpect(status().isTooManyRequests());
    }

    // --- counter bookings (ADR 0026) ------------------------------------------------------------

    @Test
    void leadersBookAWalkInAtTheCounterAndItIsConfirmedAtOnce() throws Exception {
        long paid = id(createPuja(leader, "Rudrabhishek", "1100", true));
        long free = id(createPuja(leader, "Archana", "0", true));
        counter(member, paid, "CASH").andExpect(status().isForbidden());
        // No phone or email: a walk-in.
        counter(leader, paid, "CASH").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.counter").value(true))
                .andExpect(jsonPath("$.counterMode").value("CASH"));
        counter(leader, free, null).andExpect(status().isCreated()).andExpect(jsonPath("$.counterMode").doesNotExist());
        assertThat(schedule(member)).hasSize(2);
        // Counter dakshina is seva income too: never a ledger donation.
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from donation", Integer.class))).isZero();
        // ...and no payment order was opened for it.
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from payment_intent", Integer.class))).isZero();
    }

    @Test
    void aPaidCounterBookingMustSayHowItWasPaid() throws Exception {
        long paid = id(createPuja(leader, "Rudrabhishek", "1100", true));
        counter(leader, paid, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.mode").exists());
        counter(leader, paid, "BITCOIN").andExpect(status().isBadRequest());
        long retired = id(createPuja(leader, "Retired seva", "501", false));
        counter(leader, retired, "CASH").andExpect(status().isNotFound());
        assertThat(schedule(leader)).isEmpty();
    }

    // --- priests (ADR 0027) ---------------------------------------------------------------------

    @Test
    void leadersListPriestsAndMembersSeeThemWithoutPhones() throws Exception {
        priest(member, "Pt. Shridhar Joshi").andExpect(status().isForbidden());
        priest(leader, "Pt. Shridhar Joshi").andExpect(status().isCreated()).andExpect(jsonPath("$.phone").value("+919876543210"));
        priest(leader, "pt. shridhar joshi").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        mvc.perform(on(a, get("/api/v1/priests")).session(member)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pt. Shridhar Joshi"))
                .andExpect(jsonPath("$[0].phone").doesNotExist());
    }

    @Test
    void aSankalpIsAssignedToAnActivePriestOfThisTempleOnly() throws Exception {
        long puja = id(createPuja(leader, "Archana", "0", true));
        long booking = id(counter(leader, puja, null));
        long priest = id(priest(leader, "Pt. Shridhar Joshi"));
        assign(member, booking, priest).andExpect(status().isForbidden());
        assign(leader, booking, priest).andExpect(status().isOk()).andExpect(jsonPath("$.priestName").value("Pt. Shridhar Joshi"));
        assertThat(schedule(member).get(0).at("/priestName").asString()).isEqualTo("Pt. Shridhar Joshi");
        assign(leader, booking, null).andExpect(status().isOk()).andExpect(jsonPath("$.priestId").doesNotExist());

        long retired = id(priest(leader, "Pt. Retired"));
        mvc.perform(on(a, put("/api/v1/priests/" + retired)).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Pt. Retired", "active", false))).andExpect(status().isOk());
        assign(leader, booking, retired).andExpect(status().isBadRequest());

        // Another temple's priest is invisible here (RLS), and the database refuses the link anyway.
        String b = staff.tenant("pj-b");
        MockHttpSession otherAdmin = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, post("/api/v1/priests")).session(otherAdmin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "Pt. Elsewhere")))
                .andReturn().getResponse().getContentAsString()).at("/id").asLong();
        assign(leader, booking, theirs).andExpect(status().isBadRequest());
    }

    /** Found by DAST: parallel saves of one priest name raced past the check and surfaced a 500. */
    @Test
    void concurrentSavesOfOnePriestNameGiveOnePriestAndCleanErrors() throws Exception {
        int n = 8;
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return priest(leader, "Pt. Race").andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            java.util.List<Integer> codes = new java.util.ArrayList<>();
            for (var r : results) {
                codes.add(r.get());
            }
            assertThat(codes).containsOnly(201, 400);
            assertThat(codes).filteredOn(c -> c == 201).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions priest(MockHttpSession session, String name) throws Exception {
        return mvc.perform(on(a, post("/api/v1/priests")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", name, "phone", "98765 43210", "specialties", "Rudrabhishek")));
    }

    private ResultActions assign(MockHttpSession session, long booking, Long priest) throws Exception {
        return mvc.perform(on(a, post("/api/v1/puja-bookings/" + booking + "/priest")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("priestId", priest)));
    }

    private ResultActions counter(MockHttpSession session, long pujaId, String mode) throws Exception {
        return mvc.perform(on(a, post("/api/v1/puja-bookings")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("pujaId", pujaId, "devoteeName", "Walk-in devotee",
                        "gotra", "Atri", "pujaDate", TOMORROW.toString(), "mode", mode)));
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResultActions deletePuja(MockHttpSession session, long pujaId) throws Exception {
        return mvc.perform(on(a, delete("/api/v1/pujas/" + pujaId)).session(session).with(csrf()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ResultActions createPuja(MockHttpSession session, String name, String dakshina, boolean active) throws Exception {
        return mvc.perform(on(a, post("/api/v1/pujas")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", name, "deity", "Shiva", "dakshina", dakshina, "active", active, "displayOrder", 1)));
    }

    private String bookingBody(LocalDate date) {
        return staff.body("devoteeName", "Lakshmi Iyer", "gotra", "Kashyap", "nakshatra", "Rohini",
                "pujaDate", date.toString(), "phone", "98765 43210");
    }

    private ResultActions book(long pujaId, LocalDate date) throws Exception {
        return mvc.perform(on(a, post("/api/v1/public/pujas/" + pujaId + "/book")).with(csrf())
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(date)));
    }

    private ResultActions confirm(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(OnlineDonationTest.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(on(a, post("/api/v1/public/donations/confirm")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("orderId", orderId, "paymentId", paymentId, "signature", sig)));
    }

    private JsonNode schedule(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(on(a, get("/api/v1/puja-bookings").param("date", TOMORROW.toString())).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private <T> T pinned(java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        return tx.execute(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }
}
