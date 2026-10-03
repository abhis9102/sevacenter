package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * Password reset links, issued by a trust admin and handed over like setup links. Review finding:
 * the first version returned the token to whoever posted an email to /forgot-password whenever the
 * Spring profile was local, test *or default* (i.e. a production deployment without a profile):
 * account takeover of any staff member. There is now no endpoint that gives a token to its caller.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    private static final String NEW_PASSWORD = "a-brand-new-password-42";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private app.sevacenter.tenant.TenantRepository tenants;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;
    private long leaderId;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("pr-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        leaderId = staff.userId(a, leader);
    }

    /** The takeover regression: nothing anonymous hands out a token, in any form. */
    @Test
    void noEndpointGivesAResetTokenToWhoeverAsks() throws Exception {
        String email = email(leaderId);
        String body = mvc.perform(on(a, post("/api/v1/auth/forgot-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(staff.body("email", email)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("token");
    }

    @Test
    void anAdminIssuedLinkResetsThePasswordOnce() throws Exception {
        String token = issue(admin, leaderId);
        reset(a, token, NEW_PASSWORD).andExpect(status().isNoContent());
        staff.login(a, email(leaderId), NEW_PASSWORD).andExpect(status().isOk());
        staff.login(a, email(leaderId), TestStaff.PASSWORD).andExpect(status().isUnauthorized());
        reset(a, token, "yet-another-password-99").andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsIssueLinksForActiveStaffAndNotForThemselves() throws Exception {
        mvc.perform(on(a, post("/api/v1/users/" + staff.userId(a, admin) + "/reset-link")).session(leader).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/users/" + staff.userId(a, admin) + "/reset-link")).session(admin).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("use_change_password"));
        long pending = json.readTree(mvc.perform(on(a, post("/api/v1/users")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("email", "pending@x.example", "displayName", "P", "role", "MEMBER")))
                .andReturn().getResponse().getContentAsString()).at("/user/id").asLong();
        mvc.perform(on(a, post("/api/v1/users/" + pending + "/reset-link")).session(admin).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("not_active"));
    }

    @Test
    void aNewLinkKillsTheOldOne() throws Exception {
        String first = issue(admin, leaderId);
        String second = issue(admin, leaderId);
        reset(a, first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(a, second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /** Deactivated or deleted staff stay out: their links die, and a link never revives them. */
    @Test
    void deactivatingOrDeletingStaffKillsTheirLinks() throws Exception {
        String token = issue(admin, leaderId);
        mvc.perform(on(a, post("/api/v1/users/" + leaderId + "/deactivate")).session(admin).with(csrf()))
                .andExpect(status().isOk());
        reset(a, token, NEW_PASSWORD).andExpect(status().isBadRequest());

        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        long memberId = staff.userId(a, member);
        String memberToken = issue(admin, memberId);
        mvc.perform(on(a, delete("/api/v1/users/" + memberId)).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        reset(a, memberToken, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    /*
     * Two independent layers: deactivate/delete kill the link, and redeeming requires an ACTIVE
     * user. End to end each masks the other (mutation-tested), so each is tested on its own here.
     */

    @Test
    void deactivationMarksOutstandingLinksUsed() throws Exception {
        issue(admin, leaderId);
        mvc.perform(on(a, post("/api/v1/users/" + leaderId + "/deactivate")).session(admin).with(csrf()))
                .andExpect(status().isOk());
        Integer live = pinned(() -> jdbc.queryForObject(
                "select count(*) from user_password_reset_token where user_id = ? and used_at is null", Integer.class, leaderId));
        assertThat(live).isZero();
    }

    @Test
    void aStillValidLinkCantResetAnAccountThatIsNoLongerActive() throws Exception {
        String token = issue(admin, leaderId);
        pinned(() -> jdbc.update("update app_user set status = 'DISABLED' where id = ?", leaderId));
        reset(a, token, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void aLinkOnlyWorksOnItsOwnTrustsHost() throws Exception {
        String b = staff.tenant("pr-b");
        String token = issue(admin, leaderId);
        reset(b, token, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(a, token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void weakPasswordsAreRefused() throws Exception {
        reset(a, issue(admin, leaderId), "short").andExpect(status().isBadRequest());
    }

    // --- helpers -----------------------------------------------------------------------------

    private <T> T pinned(java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(a).orElseThrow().getId();
        return tx.execute(st -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }

    private String issue(MockHttpSession session, long userId) throws Exception {
        String url = json.readTree(mvc.perform(on(a, post("/api/v1/users/" + userId + "/reset-link")).session(session)
                        .with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .at("/resetUrl").asString();
        assertThat(url).startsWith("https://" + a + ".sevacenter.app/reset-password#token=");
        return url.substring(url.indexOf("#token=") + "#token=".length());
    }

    private ResultActions reset(String slug, String token, String password) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/reset-password")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("token", token, "newPassword", password)));
    }

    private String email(long userId) throws Exception {
        String list = mvc.perform(on(a, get("/api/v1/users")).session(admin)).andReturn().getResponse().getContentAsString();
        for (var u : json.readTree(list)) {
            if (u.at("/id").asLong() == userId) {
                return u.at("/email").asString();
            }
        }
        throw new AssertionError("no user " + userId);
    }
}
