package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.auth.RegistrationService;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The trust's audit log (ADR 0020): staff actions land in it with the right actor, only when the
 * action commits, without personal data, readable by TRUST_ADMIN only, per trust, and immutable.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private PostgreSQLContainer postgres;
    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private TenantRepository tenants;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("au-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aRoleChangeIsRecordedWithWhoDidItAndWhatChanged() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        long memberId = staff.userId(a, member);
        staff.changeRole(a, admin, memberId, "LEADER");
        JsonNode entry = latest(a, admin, "USER_ROLE_CHANGED");
        assertThat(entry.at("/actor").asString()).isEqualTo("Admin");
        assertThat(entry.at("/targetType").asString()).isEqualTo("user");
        assertThat(entry.at("/targetId").asLong()).isEqualTo(memberId);
        assertThat(entry.at("/detail").asString()).isEqualTo("MEMBER -> LEADER");
        assertThat(latest(a, admin, "USER_INVITED").at("/detail").asString()).isEqualTo("MEMBER");
    }

    @Test
    void onlyTrustAdminsReadTheLogAndItIsNeverCached() throws Exception {
        MockHttpSession leader = staff.staff(a, admin, "LEADER");
        mvc.perform(on(a, get("/api/v1/audit")).session(leader)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/audit"))).andExpect(status().isUnauthorized());
        mvc.perform(on(a, get("/api/v1/audit")).session(admin)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void anActionThatFailsLeavesNoEntry() throws Exception {
        long adminId = staff.userId(a, admin);
        // The only admin can't demote themselves: 409, nothing changed, so nothing is logged.
        mvc.perform(on(a, patch("/api/v1/users/" + adminId + "/role")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("role", "MEMBER")))
                .andExpect(status().isConflict());
        mvc.perform(on(a, get("/api/v1/audit?action=USER_ROLE_CHANGED")).session(admin))
                .andExpect(jsonPath("$.total").value(0));
    }

    /** The guarantee itself: an entry commits or rolls back with its action, never on its own. */
    @Test
    void anEntryRollsBackWithItsActionAndNeedsOne() throws Exception {
        TenantContext.set(tenants.findBySlug(a).orElseThrow().getId());
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
                auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_profile", null, null);
                throw new IllegalStateException("the action fails after auditing");
            })).hasMessage("the action fails after auditing");
            assertThatThrownBy(() -> auditTrail.record(AuditAction.TEMPLE_PAGE_SAVED, "temple_profile", null, null))
                    .isInstanceOf(IllegalTransactionStateException.class);
        } finally {
            TenantContext.clear();
        }
        mvc.perform(on(a, get("/api/v1/audit?action=TEMPLE_PAGE_SAVED")).session(admin))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void devoteeAndMoneyActionsAreRecordedWithoutPersonalData() throws Exception {
        long devotee = json.readTree(mvc.perform(on(a, post("/api/v1/devotees")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("fullName", "Meenakshi Sundaram",
                        "phone", "9876543210", "email", "meenakshi@example.org", "consentSource", "IN_PERSON")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/donations")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("devoteeId", devotee, "donorName", "Meenakshi Sundaram", "amount", "1100",
                        "mode", "UPI", "receivedOn", LocalDate.now().toString()))).andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(admin)).andExpect(status().isOk());

        assertThat(latest(a, admin, "DEVOTEE_CREATED").at("/targetId").asLong()).isEqualTo(devotee);
        assertThat(latest(a, admin, "DONATION_RECORDED").at("/detail").asString()).isEqualTo("Rs 1100.00");
        assertThat(latest(a, admin, "DEVOTEES_EXPORTED").at("/detail").asString()).isEqualTo("1 rows");
        String log = mvc.perform(on(a, get("/api/v1/audit?size=100")).session(admin)).andReturn().getResponse()
                .getContentAsString();
        assertThat(log).doesNotContain("Meenakshi").doesNotContain("9876543210").doesNotContain("meenakshi@");
    }

    @Test
    void aCsvImportIsOneEntryNotOnePerRow() throws Exception {
        String csv = "fullName,phone,email,addressLine,city,state,pincode,dateOfBirth,consentSource\n"
                + "Ravi,9876543210,,,,,,,WRITTEN\nSita,9876543211,,,,,,,WRITTEN\n";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/devotees/import")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "d.csv", "text/csv", csv.getBytes()))
                        .session(admin).with(csrf()).with(r -> { r.setServerName(a + ".sevacenter.app"); return r; }))
                .andExpect(status().is2xxSuccessful());
        assertThat(latest(a, admin, "DEVOTEES_IMPORTED").at("/detail").asString()).isEqualTo("2 rows");
        mvc.perform(on(a, get("/api/v1/audit?action=DEVOTEE_CREATED")).session(admin))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void eachTrustSeesOnlyItsOwnLog() throws Exception {
        staff.staff(a, admin, "MEMBER");
        String b = staff.tenant("au-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        mvc.perform(on(b, get("/api/v1/audit")).session(adminB)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void theAppCannotEditOrEraseHistory() throws Exception {
        staff.staff(a, admin, "MEMBER");
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), TestcontainersConfiguration.APP_ROLE,
                TestcontainersConfiguration.APP_ROLE_PASSWORD); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("update audit_log set action = 'X_EDITED'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeUpdate("delete from audit_log"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    private JsonNode latest(String slug, MockHttpSession session, String action) throws Exception {
        JsonNode page = json.readTree(mvc.perform(on(slug, get("/api/v1/audit?action=" + action)).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.at("/total").asLong()).as(action + " entries").isPositive();
        return page.at("/items/0");
    }
}
