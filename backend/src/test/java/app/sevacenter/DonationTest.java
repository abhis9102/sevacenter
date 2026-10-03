package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import app.sevacenter.auth.RegistrationService;
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
 * The donation ledger (M3.1, ADR 0011): roles, exact money, append-only at the database,
 * reversals, cross-tenant ids, financial years in IST, and erasure of a donor.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DonationTest {

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

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;
    private MockHttpSession member;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("dn-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        member = staff.staff(a, admin, "MEMBER");
    }

    // --- roles -------------------------------------------------------------------------------

    @Test
    void membersSeeNothingLeadersRecordOnlyAdminsReverse() throws Exception {
        record(member, donation("1500")).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/donations")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/donations/summary")).session(member)).andExpect(status().isForbidden());

        long id = id(record(leader, donation("1500")).andExpect(status().isCreated()));
        mvc.perform(on(a, get("/api/v1/donations/" + id)).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/donations/" + id)).session(leader)).andExpect(status().isOk());
        reverse(leader, id).andExpect(status().isForbidden());
        reverse(admin, id).andExpect(status().isCreated());
    }

    // --- money -------------------------------------------------------------------------------

    @Test
    void amountsAreExactDecimalsNeverFloats() throws Exception {
        record(leader, donation("1500.50")).andExpect(jsonPath("$.amount").value("1500.50"));
        record(leader, donation("0.10"));
        record(leader, donation("0.20"));
        mvc.perform(on(a, get("/api/v1/donations/summary")).session(leader))
                .andExpect(jsonPath("$.net").value("1500.80"));
    }

    @Test
    void tamperedAmountsAreRejected() throws Exception {
        for (String bad : new String[] {"-100", "0", "0.00", "0.001", "1e5", "1,000", " 100", "100.", ".5",
                "10000000000", "NaN", "Infinity", "0x10", "١٠٠"}) {
            record(leader, donation(bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.amount").exists());
        }
    }

    // --- append-only -------------------------------------------------------------------------

    /** The app's own database role can't rewrite history: not even a bug or an injection could. */
    @Test
    void theAppRoleCannotUpdateOrDeleteLedgerRows() throws Exception {
        long id = id(record(leader, donation("1500")));
        assertThatThrownBy(() -> pinned(() -> jdbc.update("update donation set amount_paise = 1 where id = ?", id)))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> pinned(() -> jdbc.update("delete from donation where id = ?", id)))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void aDonationCanBeReversedOnceAndReversalsNetOut() throws Exception {
        long id = id(record(leader, donation("1500")));
        reverse(admin, id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("-1500.00"))
                .andExpect(jsonPath("$.reversesId").value(id));
        reverse(admin, id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("already_reversed"));

        long reversal = pinned(() -> jdbc.queryForObject("select id from donation where reverses_id = ?", Long.class, id));
        reverse(admin, reversal).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cannot_reverse_a_reversal"));

        mvc.perform(on(a, get("/api/v1/donations/summary")).session(leader))
                .andExpect(jsonPath("$.net").value("0.00"))
                .andExpect(jsonPath("$.byMode[0].donations").value(1))
                .andExpect(jsonPath("$.byMode[0].reversals").value(1));
    }

    /**
     * The guarantee behind "reversed at most once" is the database's, not the app's pre-check:
     * even two simultaneous reversals (or a buggy code path) can't both land.
     */
    @Test
    void theDatabaseRefusesASecondReversal() throws Exception {
        long id = id(record(leader, donation("1500")));
        reverse(admin, id).andExpect(status().isCreated());
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        long adminId = staff.userId(a, admin);
        assertThatThrownBy(() -> pinned(() -> jdbc.update("insert into donation (tenant_id, donor_name, amount_paise, "
                + "mode, received_on, reverses_id, reversal_reason, recorded_by) values (?, 'x', -150000, 'UPI', "
                + "current_date, ?, 'a second reversal', ?)", tenantId, id, adminId)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void aReversalNeedsAReason() throws Exception {
        long id = id(record(leader, donation("1500")));
        mvc.perform(on(a, post("/api/v1/donations/" + id + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"oops\"}")).andExpect(status().isBadRequest());
    }

    /** Ledger fields the server owns can't be set by a client, e.g. to forge a reversal. */
    @Test
    void ledgerFieldsInTheBodyAreIgnored() throws Exception {
        long first = id(record(leader, donation("1500")));
        String sneaky = donation("1500").replace("{", "{\"reversesId\":" + first + ",\"amountPaise\":-1,"
                + "\"recordedBy\":1,\"tenantId\":1,\"reversalReason\":\"forged reversal here\",");
        record(leader, sneaky).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversesId").doesNotExist())
                .andExpect(jsonPath("$.amount").value("1500.00"))
                .andExpect(jsonPath("$.recordedBy").value(staff.userId(a, leader)));
    }

    // --- donors and tenants ------------------------------------------------------------------

    @Test
    void aLinkedDevoteesNameIsSnapshottedAndAnUnlinkedDonorMustBeNamed() throws Exception {
        long devotee = devotee(a, leader, "Lakshmi Iyer");
        record(leader, donation("100").replace("\"donorName\":\"Anonymous Bhakt\",", "\"devoteeId\":" + devotee + ",\"donorName\":\"Someone Else\","))
                .andExpect(jsonPath("$.donorName").value("Lakshmi Iyer"));
        record(leader, donation("100").replace("\"donorName\":\"Anonymous Bhakt\",", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.donorName").exists());
    }

    @Test
    void anotherTenantsDonationsAndDevoteesAreNotFound() throws Exception {
        String b = staff.tenant("dn-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirDevotee = devotee(b, adminB, "Tenant B Devotee");
        long theirDonation = id(mvc.perform(on(b, post("/api/v1/donations")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(donation("999"))));

        record(leader, donation("100").replace("{", "{\"devoteeId\":" + theirDevotee + ","))
                .andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/donations/" + theirDonation)).session(admin)).andExpect(status().isNotFound());
        reverse(admin, theirDonation).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/donations")).session(admin)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void receivedDatesCantBeInTheFutureOrImplausiblyOld() throws Exception {
        record(leader, donation("100").replace(YESTERDAY.toString(), YESTERDAY.plusDays(2).toString()))
                .andExpect(status().isBadRequest());
        record(leader, donation("100").replace(YESTERDAY.toString(), "1999-12-31")).andExpect(status().isBadRequest());
    }

    // --- financial years ---------------------------------------------------------------------

    @Test
    void summariesFollowTheIndianFinancialYear() throws Exception {
        record(leader, donation("100").replace(YESTERDAY.toString(), "2025-03-31"));
        record(leader, donation("200").replace(YESTERDAY.toString(), "2025-04-01"));
        record(leader, donation("400").replace(YESTERDAY.toString(), "2026-03-31"));
        mvc.perform(on(a, get("/api/v1/donations/summary").param("fy", "2024")).session(leader))
                .andExpect(jsonPath("$.financialYear").value("2024-25")).andExpect(jsonPath("$.net").value("100.00"));
        mvc.perform(on(a, get("/api/v1/donations/summary").param("fy", "2025")).session(leader))
                .andExpect(jsonPath("$.financialYear").value("2025-26"))
                .andExpect(jsonPath("$.from").value("2025-04-01")).andExpect(jsonPath("$.to").value("2026-03-31"))
                .andExpect(jsonPath("$.net").value("600.00"));
    }

    // --- erasure of a donor ------------------------------------------------------------------

    @Test
    void erasingADonorAnonymisesThemAndKeepsTheLedger() throws Exception {
        long donor = devotee(a, leader, "Lakshmi Iyer");
        long other = devotee(a, leader, "Never Donated");
        record(leader, donation("100").replace("{", "{\"devoteeId\":" + donor + ","));

        erase(donor).andExpect(status().isNoContent());
        erase(other).andExpect(status().isNoContent());

        mvc.perform(on(a, get("/api/v1/devotees/" + donor)).session(admin)).andExpect(status().isNotFound());
        Map<String, Object> row = pinned(() -> jdbc.queryForMap(
                "select full_name, phone, email, address_line, date_of_birth, erased_at from devotee where id = ?", donor));
        assertThat(row.get("full_name")).isEqualTo("Erased devotee");
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("address_line")).isNull();
        assertThat(row.get("date_of_birth")).isNull();
        assertThat(row.get("erased_at")).isNotNull();
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from devotee where id = ?", Integer.class, other))).isZero();
        assertThat(pinned(() -> jdbc.queryForObject("select count(*) from donation where devotee_id = ?", Integer.class, donor)))
                .isEqualTo(1);
        mvc.perform(on(a, get("/api/v1/devotees")).session(admin)).andExpect(jsonPath("$.total").value(0));
        erase(donor).andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------------------------

    private String donation(String amount) {
        return "{\"donorName\":\"Anonymous Bhakt\",\"amount\":\"" + amount + "\",\"mode\":\"UPI\","
                + "\"reference\":\"UTR 412345678901\",\"purpose\":\"Annadanam\",\"receivedOn\":\"" + YESTERDAY + "\"}";
    }

    private ResultActions record(MockHttpSession session, String body) throws Exception {
        return mvc.perform(on(a, post("/api/v1/donations")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reverse(MockHttpSession session, long id) throws Exception {
        return mvc.perform(on(a, post("/api/v1/donations/" + id + "/reverse")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Entered twice by mistake\"}"));
    }

    private ResultActions erase(long devoteeId) throws Exception {
        return mvc.perform(on(a, delete("/api/v1/devotees/" + devoteeId)).session(admin).with(csrf()));
    }

    private long devotee(String slug, MockHttpSession session, String name) throws Exception {
        return id(mvc.perform(on(slug, post("/api/v1/devotees")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + name + "\",\"phone\":\"9876543210\",\"consentSource\":\"IN_PERSON\"}")));
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private <T> T pinned(java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        return tx.execute(s -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }
}
