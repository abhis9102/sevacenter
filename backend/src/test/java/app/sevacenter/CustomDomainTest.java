package app.sevacenter;

import static app.sevacenter.TestStaff.admin;
import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.domain.CustomDomainService;
import app.sevacenter.domain.DnsLookup;
import app.sevacenter.tenant.TenantContext;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A temple's MandirCenter on its own domain (ADR 0036), against a fake DNS. */
@Import({TestcontainersConfiguration.class, CustomDomainTest.Dns.class})
@SpringBootTest
@AutoConfigureMockMvc
class CustomDomainTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private FakeDns dns;
    @Autowired
    private CustomDomainService service;
    @Autowired
    private TenantRepository tenants;

    private TestStaff staff;
    private String a;
    private MockHttpSession adminA;
    private String host;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("dom-a");
        adminA = staff.loginAdmin(a);
        host = "www." + UUID.randomUUID().toString().substring(0, 8) + "-temple.org";
    }

    @Test
    void aDomainServesTheTemplePagesOnlyOnceDnsProvesOwnership() throws Exception {
        JsonNode claim = add(a, adminA, host).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.txtName").value("_sevacenter." + host)).andReturn().json();
        long id = claim.at("/id").asLong();

        // Not proven yet: nothing answers on it, and the edge may not get a certificate for it.
        mvc.perform(custom(host, get("/api/v1/public/temple"))).andExpect(status().isNotFound());
        allowed(host, 404);
        verify(a, adminA, id).andExpect(jsonPath("$.status").value("FAILED"));

        dns.txt.put("_sevacenter." + host, List.of(claim.at("/txtValue").asString()));
        verify(a, adminA, id).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(custom(host, get("/api/v1/public/temple"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.trustName").value("Trust " + a));
        allowed(host, 200);
    }

    @Test
    void staffSignInAndTheStaffApiNeverAnswerOnACustomDomain() throws Exception {
        activate(a, adminA, host);
        mvc.perform(custom(host, post("/api/v1/auth/login")).with(csrf())
                .param("email", admin(a)).param("password", TestStaff.PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(custom(host, get("/api/v1/devotees")).session(adminA)).andExpect(status().isUnauthorized());
        mvc.perform(custom(host, get("/api/v1/me")).session(adminA)).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherTrustCantTakeOverAConnectedDomain() throws Exception {
        activate(a, adminA, host);
        String b = staff.tenant("dom-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        long idB = add(b, adminB, host).andExpect(status().isCreated()).andReturn().json().at("/id").asLong();
        String tokenB = list(b, adminB).at("/domains/0/txtValue").asString();
        dns.txt.put("_sevacenter." + host, List.of(tokenB)); // even with B's proof in DNS now
        verify(b, adminB, idB).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastError").value("This domain is already connected to another temple"));
        mvc.perform(custom(host, get("/api/v1/public/temple"))).andExpect(jsonPath("$.trustName").value("Trust " + a));
    }

    @Test
    void ourOwnAddressesAndNonPublicNamesAreRefused() throws Exception {
        for (String bad : List.of("x." + a + ".sevacenter.app", "sevacenter.app", "localhost", "127.0.0.1",
                "https://www.temple.org", "temple", "www.temple.local", "www.temple.internal", "a_b.temple.org")) {
            add(a, adminA, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.hostname").exists());
        }
    }

    @Test
    void aDomainWhoseProofDisappearsIsDisconnected() throws Exception {
        activate(a, adminA, host);
        dns.txt.remove("_sevacenter." + host);
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        TenantContext.set(tenantId);
        try {
            assertThat(service.recheckActive()).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
        mvc.perform(custom(host, get("/api/v1/public/temple"))).andExpect(status().isNotFound());
        allowed(host, 404);
    }

    @Test
    void removingADomainDisconnectsIt() throws Exception {
        long id = activate(a, adminA, host);
        mvc.perform(on(a, delete("/api/v1/custom-domains/" + id)).session(adminA).with(csrf()))
                .andExpect(status().isNoContent());
        allowed(host, 404);
    }

    @Test
    void itReportsWhetherTheDomainPointsHere() throws Exception {
        long id = activate(a, adminA, host);
        verify(a, adminA, id).andExpect(jsonPath("$.pointsHere").value(false));
        dns.cname.put(host, List.of(a + ".mandircenter.app"));
        verify(a, adminA, id).andExpect(jsonPath("$.pointsHere").value(true));
    }

    @Test
    void onlyTheTrustAdminManagesDomains() throws Exception {
        MockHttpSession leader = staff.staff(a, adminA, "LEADER");
        add(a, leader, host).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/custom-domains")).session(leader)).andExpect(status().isForbidden());
    }

    // --- helpers -----------------------------------------------------------------------------

    private long activate(String slug, MockHttpSession admin, String hostname) throws Exception {
        JsonNode c = add(slug, admin, hostname).andExpect(status().isCreated()).andReturn().json();
        dns.txt.put("_sevacenter." + hostname, List.of(c.at("/txtValue").asString()));
        verify(slug, admin, c.at("/id").asLong()).andExpect(jsonPath("$.status").value("ACTIVE"));
        return c.at("/id").asLong();
    }

    private Result add(String slug, MockHttpSession session, String hostname) throws Exception {
        return new Result(mvc.perform(on(slug, post("/api/v1/custom-domains")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("hostname", hostname))));
    }

    private ResultActions verify(String slug, MockHttpSession session, long id) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/custom-domains/" + id + "/verify")).session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    private JsonNode list(String slug, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(on(slug, get("/api/v1/custom-domains")).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void allowed(String hostname, int expected) throws Exception {
        mvc.perform(get("/api/v1/public/custom-domains/allowed").param("domain", hostname))
                .andExpect(status().is(expected));
    }

    private static MockHttpServletRequestBuilder custom(String hostname, MockHttpServletRequestBuilder r) {
        return r.with(req -> {
            req.setServerName(hostname);
            return req;
        });
    }

    /** ResultActions plus the body as JSON. */
    private final class Result {
        private final ResultActions actions;

        Result(ResultActions actions) {
            this.actions = actions;
        }

        Result andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            actions.andExpect(m);
            return this;
        }

        Result andReturn() {
            return this;
        }

        JsonNode json() throws Exception {
            return CustomDomainTest.this.json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    static class FakeDns implements DnsLookup {
        final Map<String, List<String>> txt = new ConcurrentHashMap<>();
        final Map<String, List<String>> cname = new ConcurrentHashMap<>();
        final Map<String, List<String>> a = new ConcurrentHashMap<>();

        @Override
        public List<String> txt(String name) {
            return txt.getOrDefault(name, List.of());
        }

        @Override
        public List<String> cname(String name) {
            return cname.getOrDefault(name, List.of());
        }

        @Override
        public List<String> a(String name) {
            return a.getOrDefault(name, List.of());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dns {
        @Bean
        @Primary
        FakeDns fakeDns() {
            return new FakeDns();
        }
    }
}
