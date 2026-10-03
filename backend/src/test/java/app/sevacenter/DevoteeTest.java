package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Devotee records (M2, ADR 0010): tiered access, server-side masking, cross-tenant ids, mass
 * assignment, consent, input normalisation and search safety.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DevoteeTest {

    private static final String LAKSHMI = """
            {"fullName":"Lakshmi Iyer","phone":"98765 43210","email":"Lakshmi@Example.org",
             "addressLine":"12 Temple Street","city":"Pune","state":"Maharashtra","pincode":"411001",
             "dateOfBirth":"1980-05-14","consentSource":"IN_PERSON"}""";

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
        a = staff.tenant("dv-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        member = staff.staff(a, admin, "MEMBER");
    }

    // --- tiered access (ADR 0010) ------------------------------------------------------------

    @Test
    void membersReadLeadersWriteOnlyAdminsErase() throws Exception {
        create(member, LAKSHMI).andExpect(status().isForbidden());
        long id = id(create(leader, LAKSHMI).andExpect(status().isCreated()));

        mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(member)).andExpect(status().isOk());
        update(member, id, LAKSHMI).andExpect(status().isForbidden());
        update(leader, id, LAKSHMI).andExpect(status().isOk());
        erase(member, id).andExpect(status().isForbidden());
        erase(leader, id).andExpect(status().isForbidden());
        erase(admin, id).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousGetsNothing() throws Exception {
        mvc.perform(on(a, get("/api/v1/devotees"))).andExpect(status().isUnauthorized());
    }

    // --- masking -----------------------------------------------------------------------------

    @Test
    void membersSeeMaskedContactDetailsAndNoAddressOrBirthDate() throws Exception {
        long id = id(create(leader, LAKSHMI));
        String body = mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(member))
                .andExpect(jsonPath("$.masked").value(true))
                .andExpect(jsonPath("$.fullName").value("Lakshmi Iyer"))
                .andExpect(jsonPath("$.city").value("Pune"))
                .andExpect(jsonPath("$.phone").value("+91******3210"))
                .andExpect(jsonPath("$.email").value("l***@example.org"))
                .andExpect(jsonPath("$.addressLine").doesNotExist())
                .andExpect(jsonPath("$.pincode").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("9876543210", "lakshmi@", "Temple Street", "411001", "1980");

        String list = mvc.perform(on(a, get("/api/v1/devotees")).session(member))
                .andExpect(jsonPath("$.items[0].phone").value("+91******3210"))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("9876543210", "Temple Street");

        mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(leader))
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.addressLine").value("12 Temple Street"));
    }

    @Test
    void membersCanOnlySearchByNameSoTheyCantConfirmAPhoneNumber() throws Exception {
        create(leader, LAKSHMI);
        search(member, "9876543210").andExpect(jsonPath("$.total").value(0));
        search(member, "lakshmi@example").andExpect(jsonPath("$.total").value(0));
        search(member, "laksh").andExpect(jsonPath("$.total").value(1));
        search(leader, "9876543210").andExpect(jsonPath("$.total").value(1));
        search(leader, "lakshmi@example").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aDemotedLeaderSeesMaskedDataOnTheVeryNextRequest() throws Exception {
        long id = id(create(leader, LAKSHMI));
        mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(leader)).andExpect(jsonPath("$.masked").value(false));
        staff.changeRole(a, admin, staff.userId(a, leader), "MEMBER");
        mvc.perform(on(a, get("/api/v1/devotees/" + id)).session(leader)).andExpect(jsonPath("$.masked").value(true));
    }

    // --- cross-tenant ids and mass assignment ------------------------------------------------

    @Test
    void anotherTenantsDevoteeIsNotFound() throws Exception {
        String b = staff.tenant("dv-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long theirs = id(mvc.perform(on(b, post("/api/v1/devotees")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LAKSHMI)));

        mvc.perform(on(a, get("/api/v1/devotees/" + theirs)).session(admin)).andExpect(status().isNotFound());
        update(admin, theirs, LAKSHMI.replace("Lakshmi Iyer", "Hijacked")).andExpect(status().isNotFound());
        erase(admin, theirs).andExpect(status().isNotFound());
        search(admin, "").andExpect(jsonPath("$.total").value(0));
        mvc.perform(on(b, get("/api/v1/devotees/" + theirs)).session(adminB))
                .andExpect(jsonPath("$.fullName").value("Lakshmi Iyer"));
    }

    @Test
    void tenantConsentAndAuditFieldsInTheBodyAreIgnored() throws Exception {
        String b = staff.tenant("dv-b");
        long tenantB = tenants.findBySlug(b).orElseThrow().getId();
        String sneaky = LAKSHMI.replace("{", "{\"id\":999999,\"tenantId\":" + tenantB
                + ",\"consentGivenAt\":\"2000-01-01T00:00:00Z\",\"consentRecordedBy\":1,\"createdBy\":1,\"updatedBy\":1,");
        long id = id(create(leader, sneaky).andExpect(status().isCreated()));
        long leaderId = staff.userId(a, leader);

        java.util.Map<String, Object> row = pinned(a, () -> jdbc.queryForMap(
                "select tenant_id, consent_recorded_by, created_by, updated_by, consent_given_at from devotee where id = ?", id));
        assertThat(row.get("tenant_id")).isEqualTo(tenants.findBySlug(a).orElseThrow().getId());
        assertThat(row.get("consent_recorded_by")).isEqualTo(leaderId);
        assertThat(row.get("created_by")).isEqualTo(leaderId);
        assertThat(row.get("updated_by")).isEqualTo(leaderId);
        assertThat(row.get("consent_given_at").toString()).doesNotStartWith("2000");
        assertThat(id).isNotEqualTo(999999L);
    }

    @Test
    void editsRecordWhoMadeThemAndKeepTheCreator() throws Exception {
        long id = id(create(leader, LAKSHMI));
        update(admin, id, LAKSHMI.replace("Pune", "Nashik")).andExpect(status().isOk());
        java.util.Map<String, Object> row = pinned(a, () -> jdbc.queryForMap(
                "select created_by, updated_by, consent_recorded_by from devotee where id = ?", id));
        assertThat(row.get("created_by")).isEqualTo(staff.userId(a, leader));
        assertThat(row.get("consent_recorded_by")).isEqualTo(staff.userId(a, leader));
        assertThat(row.get("updated_by")).isEqualTo(staff.userId(a, admin));
    }

    // --- consent -----------------------------------------------------------------------------

    @Test
    void consentIsRequiredAndCannotBeChangedByAnEdit() throws Exception {
        create(leader, LAKSHMI.replace(",\"consentSource\":\"IN_PERSON\"", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.consentSource").exists());
        long id = id(create(leader, LAKSHMI));
        update(leader, id, LAKSHMI.replace("IN_PERSON", "WRITTEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentSource").value("IN_PERSON"));
    }

    // --- input -------------------------------------------------------------------------------

    @Test
    void phonesAreNormalisedToE164() throws Exception {
        create(leader, LAKSHMI.replace("98765 43210", "098765-43210")).andExpect(jsonPath("$.phone").value("+919876543210"));
        create(leader, LAKSHMI.replace("98765 43210", "+44 20 7946 0958")).andExpect(jsonPath("$.phone").value("+442079460958"));
        create(leader, LAKSHMI.replace("98765 43210", "12345"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.phone").exists());
    }

    /** Every V5 check has a matching request rule: bad input is a 400, never a database error. */
    @Test
    void invalidInputIsRejectedBeforeTheDatabase() throws Exception {
        create(leader, LAKSHMI.replace("411001", "011001")).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("1980-05-14", "1899-12-31")).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("1980-05-14", "2999-01-01")).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("Lakshmi Iyer", "x".repeat(121))).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("12 Temple Street", "x".repeat(201))).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("Lakshmi Iyer", "  ")).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("IN_PERSON", "TELEPATHY")).andExpect(status().isBadRequest());
    }

    /** Postgres rejects NUL in text; found by DAST as 500s on create and search. */
    @Test
    void nulBytesAreABadRequestNotAServerError() throws Exception {
        create(leader, LAKSHMI.replace("Lakshmi Iyer", "Laks\\u0000hmi")).andExpect(status().isBadRequest());
        create(leader, LAKSHMI.replace("Pune", "Pu\\u0000ne")).andExpect(status().isBadRequest());
        mvc.perform(on(a, get("/api/v1/devotees").param("q", "a\u0000b")).session(leader))
                .andExpect(status().isBadRequest());
    }

    // --- search safety -----------------------------------------------------------------------

    /** Both queries: members search by name only, leaders by name/phone/email. */
    @Test
    void likeWildcardsInSearchMatchLiterally() throws Exception {
        create(leader, LAKSHMI);
        for (MockHttpSession session : new MockHttpSession[] {member, leader}) {
            search(session, "%").andExpect(jsonPath("$.total").value(0));
            search(session, "_").andExpect(jsonPath("$.total").value(0));
            search(session, "Laksh%").andExpect(jsonPath("$.total").value(0));
            search(session, "").andExpect(jsonPath("$.total").value(1));
        }
    }

    @Test
    void pageSizeIsCappedAndBadPagingIsClamped() throws Exception {
        mvc.perform(on(a, get("/api/v1/devotees").param("size", "100000")).session(leader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mvc.perform(on(a, get("/api/v1/devotees").param("page", "-5").param("size", "0")).session(leader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
        mvc.perform(on(a, get("/api/v1/devotees").param("sort", "password")).session(leader)).andExpect(status().isOk());
    }

    // --- erasure -----------------------------------------------------------------------------

    @Test
    void erasureRemovesThePersonalData() throws Exception {
        long id = id(create(leader, LAKSHMI));
        erase(admin, id).andExpect(status().isNoContent());
        Integer left = pinned(a, () -> jdbc.queryForObject("select count(*) from devotee where id = ?", Integer.class, id));
        assertThat(left).isZero();
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResultActions create(MockHttpSession session, String body) throws Exception {
        return mvc.perform(on(a, post("/api/v1/devotees")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(MockHttpSession session, long id, String body) throws Exception {
        return mvc.perform(on(a, put("/api/v1/devotees/" + id)).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions erase(MockHttpSession session, long id) throws Exception {
        return mvc.perform(on(a, delete("/api/v1/devotees/" + id)).session(session).with(csrf()));
    }

    private ResultActions search(MockHttpSession session, String q) throws Exception {
        return mvc.perform(on(a, get("/api/v1/devotees").param("q", q)).session(session)).andExpect(status().isOk());
    }

    private long id(ResultActions created) throws Exception {
        JsonNode node = json.readTree(created.andReturn().getResponse().getContentAsString());
        return node.at("/id").asLong();
    }

    private <T> T pinned(String slug, java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(slug).orElseThrow().getId();
        return tx.execute(s -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }
}
