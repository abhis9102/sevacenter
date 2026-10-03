package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Earmarked donation funds (ADR 0022): a managed per-trust list, donations (staff and online) given
 * to active funds only, reversals netting out of the same fund, per-fund totals, admin-managed.
 */
@Import({TestcontainersConfiguration.class, OnlineDonationTest.FakeGatewayConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class FundTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private OnlineDonationTest.FakeGateway gateway;
    @Autowired
    private PostgreSQLContainer postgres;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("fu-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void adminsManageFundsLeadersSeeThemMembersDont() throws Exception {
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        saveFund(leader, null, "Annadanam fund", true).andExpect(status().isForbidden());
        long id = fund("Annadanam fund");
        mvc.perform(on(a, get("/api/v1/donation-funds")).session(leader)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(on(a, get("/api/v1/donation-funds")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/audit?action=FUND_SAVED")).session(admin))
                .andExpect(jsonPath("$.items[0].targetId").value(id));
    }

    @Test
    void fundNamesAreUniqueIgnoringCaseAndSpaces() throws Exception {
        fund("Building Fund");
        saveFund(admin, null, "  building   fund ", true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    /** Found by DAST: parallel saves of one name raced past the check and surfaced a 500. */
    @Test
    void concurrentSavesOfOneNameGiveOneFundAndCleanErrors() throws Exception {
        int n = 8;
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return saveFund(admin, null, "Race fund", true).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            java.util.List<Integer> statuses = new java.util.ArrayList<>();
            for (var r : results) {
                statuses.add(r.get());
            }
            org.assertj.core.api.Assertions.assertThat(statuses).containsOnly(201, 400);
            org.assertj.core.api.Assertions.assertThat(statuses).filteredOn(st -> st == 201).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void donationsAreTotalledPerFundAndAReversalNetsOutOfItsFund() throws Exception {
        long annadanam = fund("Annadanam fund");
        long kept = donation("1100", annadanam);
        long reversed = donation("500", annadanam);
        donation("251", null);
        mvc.perform(on(a, post("/api/v1/donations/" + reversed + "/reverse")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Entered twice by mistake\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fundId").value(annadanam));
        mvc.perform(on(a, get("/api/v1/donations/summary")).session(admin))
                .andExpect(jsonPath("$.net").value("1351.00"))
                .andExpect(jsonPath("$.byFund[0].fund").value("General fund"))
                .andExpect(jsonPath("$.byFund[0].net").value("251.00"))
                .andExpect(jsonPath("$.byFund[1].fund").value("Annadanam fund"))
                .andExpect(jsonPath("$.byFund[1].net").value("1100.00"))
                .andExpect(jsonPath("$.byFund[1].donations").value(2));
        mvc.perform(on(a, get("/api/v1/donations/" + kept)).session(admin)).andExpect(jsonPath("$.fundId").value(annadanam));
    }

    @Test
    void onlyActiveFundsOfThisTrustTakeNewDonations() throws Exception {
        long old = fund("Old roof fund");
        saveFund(admin, old, "Old roof fund", false).andExpect(status().isOk());
        record("100", old).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fundId").exists());
        String b = staff.tenant("fu-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, post("/api/v1/donation-funds")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "B's fund")))
                .andReturn().getResponse().getContentAsString()).at("/id").asLong();
        record("100", theirs).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fundId").exists());
        saveFund(admin, theirs, "Hijacked", true).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/public/donation-funds"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anOnlineDonorCanEarmarkTheirGift() throws Exception {
        long annadanam = fund("Annadanam fund");
        mvc.perform(on(a, get("/api/v1/public/donation-funds")))
                .andExpect(jsonPath("$[0].name").value("Annadanam fund")).andExpect(jsonPath("$[0].active").doesNotExist());
        mvc.perform(on(a, put("/api/v1/payment-settings")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("keyId", OnlineDonationTest.KEY_ID, "keySecret", OnlineDonationTest.SECRET)))
                .andExpect(status().isOk());
        String orderId = json.readTree(mvc.perform(on(a, post("/api/v1/public/donations/orders")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("amount", "501", "donorName", "Lakshmi", "fundId", annadanam)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/orderId").asString();
        String paymentId = gateway.pay(orderId, 50100, "upi");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(OnlineDonationTest.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        long donation = json.readTree(mvc.perform(on(a, post("/api/v1/public/donations/confirm")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("orderId", orderId, "paymentId", paymentId, "signature", sig)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/donationId").asLong();
        mvc.perform(on(a, get("/api/v1/donations/" + donation)).session(admin)).andExpect(jsonPath("$.fundId").value(annadanam));
    }

    @Test
    void anOnlineOrderCantNameAnInactiveOrAnotherTrustsFund() throws Exception {
        mvc.perform(on(a, put("/api/v1/payment-settings")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("keyId", OnlineDonationTest.KEY_ID, "keySecret", OnlineDonationTest.SECRET)))
                .andExpect(status().isOk());
        long old = fund("Old roof fund");
        saveFund(admin, old, "Old roof fund", false).andExpect(status().isOk());
        String b = staff.tenant("fu-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, post("/api/v1/donation-funds")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "B's fund")))
                .andReturn().getResponse().getContentAsString()).at("/id").asLong();
        for (long fundId : new long[] {old, theirs}) {
            mvc.perform(on(a, post("/api/v1/public/donations/orders")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(staff.body("amount", "501", "donorName", "Lakshmi", "fundId", fundId)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fundId").exists());
        }
    }

    /** Defence in depth: even a direct insert can't point a ledger entry at another trust's fund. */
    @Test
    void theDatabaseRefusesACrossTrustFundLink() throws Exception {
        long d = donation("100", null);
        String b = staff.tenant("fu-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = json.readTree(mvc.perform(on(b, post("/api/v1/donation-funds")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "B's fund")))
                .andReturn().getResponse().getContentAsString()).at("/id").asLong();
        try (java.sql.Connection c = postgres.createConnection("");
             java.sql.PreparedStatement st = c.prepareStatement(
                     "insert into donation (tenant_id, donor_name, amount_paise, mode, received_on, recorded_by, fund_id) "
                             + "select tenant_id, 'X', 100, 'CASH', current_date, recorded_by, ? from donation where id = ?")) {
            st.setLong(1, theirs);
            st.setLong(2, d);
            org.assertj.core.api.Assertions.assertThatThrownBy(st::executeUpdate)
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("fk_donation_fund");
        }
    }

    private long fund(String name) throws Exception {
        return json.readTree(saveFund(admin, null, name, true).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).at("/id").asLong();
    }

    private ResultActions saveFund(MockHttpSession session, Long id, String name, boolean active) throws Exception {
        return mvc.perform(on(a, id == null ? post("/api/v1/donation-funds") : put("/api/v1/donation-funds/" + id))
                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", name, "active", active)));
    }

    private ResultActions record(String amount, Long fundId) throws Exception {
        return mvc.perform(on(a, post("/api/v1/donations")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("donorName", "Lakshmi", "amount", amount, "mode", "CASH", "fundId", fundId,
                        "receivedOn", LocalDate.now().toString())));
    }

    private long donation(String amount, Long fundId) throws Exception {
        return json.readTree(record(amount, fundId).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).at("/id").asLong();
    }
}
