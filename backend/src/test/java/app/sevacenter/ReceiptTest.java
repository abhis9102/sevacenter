package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.donation.ReceiptService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 80G receipts (M3.2, ADR 0012): gapless numbering per FY (also under concurrency), PAN only
 * as ciphertext, the 80G rules, cancellation on reversal, snapshots, roles and tenants.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReceiptTest {

    private static final String PAN = "ABCPE1234F";
    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

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
    private ReceiptService receipts;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("rc-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        profile(a, admin, "Shri Siddheshwar Seva Trust").andExpect(status().isOk());
    }

    // --- roles -------------------------------------------------------------------------------

    @Test
    void membersSeeNothingLeadersIssueOnlyAdminsEditTheTrustProfile() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        long d = donation(a, leader, "1500", "UPI", YESTERDAY);
        issue(member, d, PAN).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/trust-profile")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/receipts")).session(member)).andExpect(status().isForbidden());
        profile(a, leader, "Hijacked Trust").andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/trust-profile")).session(leader)).andExpect(status().isOk());
        issue(leader, d, PAN).andExpect(status().isCreated());
    }

    // --- numbering ---------------------------------------------------------------------------

    @Test
    void numbersAreSequentialPerFinancialYearAndPerTrust() throws Exception {
        assertThat(number(issue(leader, donation(a, leader, "100", "UPI", LocalDate.of(2025, 4, 1)), PAN)))
                .isEqualTo("2025-26/000001");
        assertThat(number(issue(leader, donation(a, leader, "100", "UPI", LocalDate.of(2026, 3, 31)), PAN)))
                .isEqualTo("2025-26/000002");
        assertThat(number(issue(leader, donation(a, leader, "100", "UPI", LocalDate.of(2026, 4, 1)), PAN)))
                .isEqualTo("2026-27/000001");

        String b = staff.tenant("rc-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        profile(b, adminB, "Another Trust");
        assertThat(number(mvc.perform(on(b, post("/api/v1/donations/" + donation(b, adminB, "100", "UPI",
                        LocalDate.of(2025, 6, 1)) + "/receipt")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(issueBody(PAN))))).isEqualTo("2025-26/000001");
    }

    /** Concurrent issuers serialize on the counter row: every number used once, none skipped. */
    @Test
    void concurrentIssuesGetUniqueConsecutiveNumbers() throws Exception {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        long issuer = staff.userId(a, leader);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ids.add(donation(a, leader, "100", "UPI", LocalDate.of(2025, 5, 1)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> numbers = new ArrayList<>();
            for (long id : ids) {
                numbers.add(pool.submit(() -> {
                    TenantContext.set(tenantId);
                    try {
                        return receipts.issue(id, PAN, "1 Temple Street", issuer).number();
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            List<String> got = new ArrayList<>();
            for (Future<String> n : numbers) {
                got.add(n.get());
            }
            assertThat(got).containsExactlyInAnyOrder("2025-26/000001", "2025-26/000002", "2025-26/000003",
                    "2025-26/000004", "2025-26/000005", "2025-26/000006", "2025-26/000007", "2025-26/000008");
        } finally {
            pool.shutdownNow();
        }
    }

    // --- PAN ---------------------------------------------------------------------------------

    @Test
    void thePanIsStoredOnlyEncryptedAndShownInFullOnlyOnTheReceipt() throws Exception {
        long d = donation(a, leader, "1500", "UPI", YESTERDAY);
        long id = id(issue(leader, d, "abcpe 1234f").andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.donorPan").value(PAN)));

        Map<String, Object> row = pinned(a, () -> jdbc.queryForMap(
                "select donor_pan_enc, donor_pan_last4, donor_pan_index from receipt where id = ?", id));
        assertThat(row.get("donor_pan_enc").toString()).startsWith("v1:").doesNotContain(PAN, "ABCPE");
        assertThat(row.get("donor_pan_last4")).isEqualTo("234F");
        assertThat(row.get("donor_pan_index").toString()).doesNotContain(PAN).hasSize(64);
        String everything = pinned(a, () -> jdbc.queryForList("select * from receipt where id = ?", id).toString());
        assertThat(everything).doesNotContain(PAN);

        mvc.perform(on(a, get("/api/v1/receipts/" + id)).session(leader))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.donorPan").value(PAN));
        String list = mvc.perform(on(a, get("/api/v1/receipts").param("fy", String.valueOf(fy(YESTERDAY)))).session(leader))
                .andExpect(jsonPath("$.items[0].donorPan").value("XXXXXX234F"))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(PAN);
    }

    @Test
    void anInvalidPanIsRejectedWithoutEchoingIt() throws Exception {
        long d = donation(a, leader, "1500", "UPI", YESTERDAY);
        String body = issue(leader, d, "ZZZZZ9999Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.donorPan").exists()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("ZZZZZ9999Z");
    }

    // --- 80G rules ---------------------------------------------------------------------------

    @Test
    void cashAboveTwoThousandRupeesIsNotEligible() throws Exception {
        issue(leader, donation(a, leader, "2000.01", "CASH", YESTERDAY), PAN)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("cash_over_2000_not_eligible"));
        issue(leader, donation(a, leader, "2000", "CASH", YESTERDAY), PAN).andExpect(status().isCreated());
        issue(leader, donation(a, leader, "50000", "UPI", YESTERDAY), PAN).andExpect(status().isCreated());
    }

    @Test
    void oneReceiptPerDonationAndNoneForReversalsOrReversedDonations() throws Exception {
        long d = donation(a, leader, "1500", "UPI", YESTERDAY);
        issue(leader, d, PAN).andExpect(status().isCreated());
        issue(leader, d, PAN).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("already_receipted"));

        long other = donation(a, leader, "1500", "UPI", YESTERDAY);
        reverse(other);
        issue(leader, other, PAN).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("donation_reversed"));
        long reversal = pinned(a, () -> jdbc.queryForObject("select id from donation where reverses_id = ?", Long.class, other));
        issue(leader, reversal, PAN).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("not_a_donation"));
    }

    @Test
    void theTrustNeedsAProfileWhose80GRegistrationCoversTheDonationDate() throws Exception {
        String b = staff.tenant("rc-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long d = donation(b, adminB, "100", "UPI", YESTERDAY);
        mvc.perform(on(b, post("/api/v1/donations/" + d + "/receipt")).session(adminB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(PAN)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("trust_profile_missing"));

        long old = donation(a, leader, "100", "UPI", LocalDate.of(2019, 6, 1));
        issue(leader, old, PAN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("registration_not_valid_on_donation_date"));
    }

    // --- cancellation and snapshots ----------------------------------------------------------

    @Test
    void reversingAReceiptedDonationCancelsTheReceiptAndItsNumberIsNeverReused() throws Exception {
        long d = donation(a, leader, "1500", "UPI", LocalDate.of(2025, 7, 1));
        long id = id(issue(leader, d, PAN));
        reverse(d);
        mvc.perform(on(a, get("/api/v1/receipts/" + id)).session(leader))
                .andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.number").value("2025-26/000001"));
        assertThat(number(issue(leader, donation(a, leader, "100", "UPI", LocalDate.of(2025, 7, 2)), PAN)))
                .isEqualTo("2025-26/000002");
    }

    @Test
    void anIssuedReceiptKeepsTheTrustDetailsItWasIssuedWith() throws Exception {
        long id = id(issue(leader, donation(a, leader, "1500", "UPI", YESTERDAY), PAN));
        profile(a, admin, "Renamed Trust").andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/receipts/" + id)).session(leader))
                .andExpect(jsonPath("$.trustLegalName").value("Shri Siddheshwar Seva Trust"));
    }

    // --- tenants and the database ------------------------------------------------------------

    @Test
    void anotherTenantsReceiptsAndDonationsAreNotFound() throws Exception {
        String b = staff.tenant("rc-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        profile(b, adminB, "Another Trust");
        long theirDonation = donation(b, adminB, "100", "UPI", YESTERDAY);
        long theirReceipt = id(mvc.perform(on(b, post("/api/v1/donations/" + theirDonation + "/receipt")).session(adminB)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(issueBody(PAN))));
        mvc.perform(on(a, get("/api/v1/receipts/" + theirReceipt)).session(admin)).andExpect(status().isNotFound());
        issue(admin, theirDonation, PAN).andExpect(status().isNotFound());
    }

    @Test
    void theAppRoleCannotEditOrDeleteReceipts() throws Exception {
        long id = id(issue(leader, donation(a, leader, "1500", "UPI", YESTERDAY), PAN));
        assertThatThrownBy(() -> pinned(a, () -> jdbc.update("update receipt set amount_paise = 1 where id = ?", id)))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> pinned(a, () -> jdbc.update("delete from receipt where id = ?", id)))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResultActions profile(String slug, MockHttpSession session, String name) throws Exception {
        return mvc.perform(on(slug, put("/api/v1/trust-profile")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalName\":\"" + name + "\","
                        + "\"address\":\"1 Temple Road, Pune 411001\",\"pan\":\"AAATS1234F\","
                        + "\"registration80g\":\"AAATS1234FF20214\",\"validFrom\":\"2021-04-01\",\"validTo\":\"2030-03-31\"}"));
    }

    private long donation(String slug, MockHttpSession session, String amount, String mode, LocalDate on) throws Exception {
        return id(mvc.perform(on(slug, post("/api/v1/donations")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"donorName\":\"Lakshmi Iyer\",\"amount\":\"" + amount
                        + "\",\"mode\":\"" + mode + "\",\"receivedOn\":\"" + on + "\"}")).andExpect(status().isCreated()));
    }

    private ResultActions issue(MockHttpSession session, long donationId, String pan) throws Exception {
        return mvc.perform(on(a, post("/api/v1/donations/" + donationId + "/receipt")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(issueBody(pan)));
    }

    private static String issueBody(String pan) {
        return "{\"donorPan\":\"" + pan + "\",\"donorAddress\":\"12 Temple Street, Pune 411001\"}";
    }

    private void reverse(long donationId) throws Exception {
        mvc.perform(on(a, post("/api/v1/donations/" + donationId + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Entered twice by mistake\"}"))
                .andExpect(status().isCreated());
    }

    private String number(ResultActions issued) throws Exception {
        return json.readTree(issued.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/number").asString();
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private static int fy(LocalDate day) {
        return day.getMonthValue() >= 4 ? day.getYear() : day.getYear() - 1;
    }

    private <T> T pinned(String slug, java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(slug).orElseThrow().getId();
        return tx.execute(s -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }
}
