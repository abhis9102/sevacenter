package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.user.AppUserRepository;
import app.sevacenter.user.SetupTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Staff management (M1 slice 2b, ADR 0009): role-based authorization (threat model invariant 5),
 * one-time setup links, mass assignment, cross-tenant ids, the last-admin rule (including a
 * concurrent race), and immediate revocation of deactivated or demoted users.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserManagementTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JsonMapper json;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private SetupTokenRepository setupTokens;

    private String a;
    private String b;
    private String ip;
    private MockHttpSession adminA;

    @BeforeEach
    void setUp() throws Exception {
        ip = "10.8." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        a = register("um-a");
        b = register("um-b");
        adminA = login(a, admin(a), PASSWORD);
    }

    // --- invariant 5: role-based authorization -----------------------------------------------

    @Test
    void membersCannotListUsersLeadersAndAdminsCan() throws Exception {
        MockHttpSession leader = loginAs(a, onboard(a, adminA, "leader@x.example", "LEADER"));
        MockHttpSession member = loginAs(a, onboard(a, adminA, "member@x.example", "MEMBER"));

        mvc.perform(on(a, get("/api/v1/users")).session(adminA)).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/users")).session(leader)).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/users")).session(member)).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCanCreateChangeRolesDeactivateOrReissueLinks() throws Exception {
        MockHttpSession leader = loginAs(a, onboard(a, adminA, "leader@x.example", "LEADER"));
        long target = userId(createUser(a, adminA, "target@x.example", "MEMBER"));

        mvc.perform(on(a, post("/api/v1/users")).session(leader).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "new@x.example", "displayName", "New", "role", "MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, patch("/api/v1/users/" + target + "/role")).session(leader).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("role", "TRUST_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/users/" + target + "/deactivate")).session(leader).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/users/" + target + "/setup-link")).session(leader).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- setup links -------------------------------------------------------------------------

    @Test
    void newUsersArePendingAndOnlyTheTokenHashIsStored() throws Exception {
        JsonNode created = createUser(a, adminA, "pending@x.example", "MEMBER");
        assertThat(created.at("/user/status").asString()).isEqualTo("PENDING");
        String url = created.at("/setupUrl").asString();
        assertThat(url).startsWith("https://" + a + ".sevacenter.app/setup#token=");

        String token = token(url);
        List<String> stored = pinned(a, () -> jdbc.queryForList(
                "select token_hash from user_setup_token where user_id = ?", String.class, userId(created)));
        assertThat(stored).containsExactly(sha256(token)).doesNotContain(token);

        login(a, "pending@x.example", PASSWORD, 401); // no password yet
    }

    @Test
    void aSetupLinkWorksOnceAndThenTheUserCanLogIn() throws Exception {
        String token = token(createUser(a, adminA, "once@x.example", "MEMBER").at("/setupUrl").asString());
        setup(a, token, PASSWORD).andExpect(status().isNoContent());
        String hash = sha256(token);
        Integer used = pinned(a, () -> jdbc.queryForObject(
                "select count(*) from user_setup_token where token_hash = ? and used_at is not null",
                Integer.class, hash));
        assertThat(used).as("the token itself is marked used").isEqualTo(1);
        login(a, "once@x.example", PASSWORD);
        // Rejected twice over: the token is used, and the user is no longer PENDING.
        setup(a, token, "another-password-12345").andExpect(status().isBadRequest());
    }

    @Test
    void expiredLinksAreRejected() throws Exception {
        JsonNode created = createUser(a, adminA, "late@x.example", "MEMBER");
        pinned(a, () -> jdbc.update(
                "update user_setup_token set expires_at = now() - interval '1 minute' where user_id = ?",
                userId(created)));
        setup(a, token(created.at("/setupUrl").asString()), PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void aLinkOnlyWorksOnItsOwnTenantsHost() throws Exception {
        String token = token(createUser(a, adminA, "host@x.example", "MEMBER").at("/setupUrl").asString());
        setup(b, token, PASSWORD).andExpect(status().isBadRequest());
        setup(a, token, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void reissuingALinkKillsTheOldOne() throws Exception {
        JsonNode created = createUser(a, adminA, "reissue@x.example", "MEMBER");
        String old = token(created.at("/setupUrl").asString());
        String fresh = token(json.readTree(mvc.perform(on(a, post("/api/v1/users/" + userId(created) + "/setup-link"))
                        .session(adminA).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/setupUrl").asString());

        setup(a, old, PASSWORD).andExpect(status().isBadRequest());
        setup(a, fresh, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void linksAreOnlyIssuedToPendingUsers() throws Exception {
        long active = onboard(a, adminA, "active@x.example", "MEMBER");
        mvc.perform(on(a, post("/api/v1/users/" + active + "/setup-link")).session(adminA).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("not_pending"));
        assertThat(liveTokensFor(a, active)).isZero();
    }

    /*
     * Setup has two independent checks: the token is unused, and the user is still PENDING. An
     * end-to-end replay trips both at once, so removing either went unnoticed (mutation-tested).
     * Each test below defeats one check through the database to prove the other on its own.
     */

    @Test
    void aUsedTokenIsRejectedEvenWhileTheUserIsStillPending() throws Exception {
        JsonNode created = createUser(a, adminA, "used@x.example", "MEMBER");
        pinned(a, () -> jdbc.update("update user_setup_token set used_at = now() where user_id = ?", userId(created)));
        setup(a, token(created.at("/setupUrl").asString()), PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void aValidTokenCannotResetAnActiveUsersPassword() throws Exception {
        long active = onboard(a, adminA, "takeover@x.example", "TRUST_ADMIN");
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        String forged = "planted-token-" + UUID.randomUUID();
        String hash = sha256(forged);
        pinned(a, () -> jdbc.update("insert into user_setup_token (tenant_id, user_id, token_hash, expires_at) "
                + "values (?, ?, ?, now() + interval '1 hour')", tenantA, active, hash));

        setup(a, forged, "attacker-chosen-password").andExpect(status().isBadRequest());
        login(a, "takeover@x.example", PASSWORD);
    }

    @Test
    void deactivatingAPendingUserDeletesTheirLink() throws Exception {
        JsonNode created = createUser(a, adminA, "never@x.example", "MEMBER");
        mvc.perform(on(a, post("/api/v1/users/" + userId(created) + "/deactivate")).session(adminA).with(csrf()))
                .andExpect(status().isOk());
        assertThat(liveTokensFor(a, userId(created))).isZero();
    }

    /**
     * Redemption locks the token row, so two concurrent uses of one link are serialized and the
     * second sees it used. Tested like the admin lock: while one transaction holds it, another
     * can't take it.
     */
    @Test
    void aTokenIsLockedWhileBeingRedeemed() throws Exception {
        String hash = sha256(token(createUser(a, adminA, "race@x.example", "MEMBER").at("/setupUrl").asString()));
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                pin(tenantA);
                assertThat(setupTokens.findByTokenHash(hash)).isPresent();
                locked.countDown();
                await(release);
            }));
            locked.await();
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                pin(tenantA);
                jdbc.execute("set local lock_timeout = '300ms'");
                setupTokens.findByTokenHash(hash);
            })).isInstanceOf(CannotAcquireLockException.class)
                    .rootCause().hasMessageContaining("lock timeout");
            release.countDown();
            holder.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void weakPasswordsAreRefusedAtSetup() throws Exception {
        String token = token(createUser(a, adminA, "weak@x.example", "MEMBER").at("/setupUrl").asString());
        setup(a, token, "short").andExpect(status().isBadRequest());
    }

    // --- mass assignment and cross-tenant ids ------------------------------------------------

    @Test
    void tenantStatusAndPasswordInTheRequestBodyAreIgnored() throws Exception {
        long tenantB = tenants.findBySlug(b).orElseThrow().getId();
        String sneaky = "{\"email\":\"sneaky@x.example\",\"displayName\":\"S\",\"role\":\"MEMBER\","
                + "\"tenantId\":" + tenantB + ",\"status\":\"ACTIVE\",\"passwordHash\":\"{noop}" + PASSWORD + "\"}";
        JsonNode created = json.readTree(mvc.perform(on(a, post("/api/v1/users")).session(adminA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(sneaky))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.at("/user/status").asString()).isEqualTo("PENDING");
        login(a, "sneaky@x.example", PASSWORD, 401);
        Integer inB = pinned(b, () -> jdbc.queryForObject(
                "select count(*) from app_user where email = 'sneaky@x.example'", Integer.class));
        assertThat(inB).isZero();
    }

    @Test
    void anotherTenantsUserIdIsNotFound() throws Exception {
        MockHttpSession adminB = login(b, admin(b), PASSWORD);
        long userInB = userId(createUser(b, adminB, "victim@x.example", "MEMBER"));

        mvc.perform(on(a, patch("/api/v1/users/" + userInB + "/role")).session(adminA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("role", "TRUST_ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(on(a, post("/api/v1/users/" + userInB + "/deactivate")).session(adminA).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailsAreRejected() throws Exception {
        createUser(a, adminA, "dup@x.example", "MEMBER");
        mvc.perform(on(a, post("/api/v1/users")).session(adminA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "DUP@x.example", "displayName", "Dup", "role", "MEMBER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("email_taken"));
    }

    /**
     * Delete and link-issuing for the same user serialize on the user row (DAST found a reissue
     * racing a delete: a token inserted for a just-deleted user, a foreign-key 500).
     */
    @Test
    void linkIssuingAndDeletionLockTheUserRow() throws Exception {
        long id = userId(createUser(a, adminA, "racer@x.example", "MEMBER"));
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(st -> {
                pin(tenantA);
                assertThat(users.findLockedByIdAndDeletedAtIsNull(id)).isPresent();
                locked.countDown();
                await(release);
            }));
            locked.await();
            assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
                pin(tenantA);
                jdbc.execute("set local lock_timeout = '300ms'");
                users.findLockedByIdAndDeletedAtIsNull(id);
            })).isInstanceOf(CannotAcquireLockException.class);
            release.countDown();
            holder.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // --- deleting staff ----------------------------------------------------------------------

    @Test
    void onlyAdminsCanDeleteStaffAndNobodyCanDeleteThemselves() throws Exception {
        long leaderId = onboard(a, adminA, "leader@x.example", "LEADER");
        MockHttpSession leader = loginAs(a, leaderId);
        long target = userId(createUser(a, adminA, "target@x.example", "MEMBER"));
        mvc.perform(on(a, delete("/api/v1/users/" + target)).session(leader).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(on(a, delete("/api/v1/users/" + meId(a, adminA))).session(adminA).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("cannot_delete_self"));
    }

    @Test
    void deletingAnInvitationRemovesItAndFreesTheEmail() throws Exception {
        JsonNode created = createUser(a, adminA, "invitee@x.example", "MEMBER");
        long id = userId(created);
        mvc.perform(on(a, delete("/api/v1/users/" + id)).session(adminA).with(csrf())).andExpect(status().isNoContent());

        assertThat(pinned(a, () -> jdbc.queryForObject("select count(*) from app_user where id = ?", Integer.class, id))).isZero();
        setup(a, token(created.at("/setupUrl").asString()), PASSWORD).andExpect(status().isBadRequest());
        createUser(a, adminA, "invitee@x.example", "MEMBER"); // the email can be invited again
    }

    /**
     * Someone who could have acted is tombstoned, not removed: their records (devotee changes,
     * consent, donations) keep pointing at a row that still says who they were.
     */
    @Test
    void deletingActiveStaffEndsTheirAccessButKeepsWhoTheyWereForTheRecords() throws Exception {
        long id = onboard(a, adminA, "leaver@x.example", "LEADER");
        MockHttpSession leaver = loginAs(a, id);
        mvc.perform(on(a, post("/api/v1/devotees")).session(leaver).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Recorded By Leaver\",\"consentSource\":\"IN_PERSON\"}"))
                .andExpect(status().isCreated());

        mvc.perform(on(a, delete("/api/v1/users/" + id)).session(adminA).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(on(a, get("/api/v1/me")).session(leaver)).andExpect(status().isUnauthorized());
        login(a, "leaver@x.example", PASSWORD, 401);
        String list = mvc.perform(on(a, get("/api/v1/users")).session(adminA)).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("leaver@x.example", "deleted-");
        changeRole(a, adminA, id, "TRUST_ADMIN").andExpect(status().isNotFound());
        mvc.perform(on(a, delete("/api/v1/users/" + id)).session(adminA).with(csrf())).andExpect(status().isNotFound());

        java.util.Map<String, Object> row = pinned(a, () -> jdbc.queryForMap(
                "select email, display_name, password_hash, status, deleted_at from app_user where id = ?", id));
        assertThat(row.get("email")).isEqualTo("deleted-" + id + "@users.invalid");
        assertThat(row.get("display_name")).isEqualTo("User");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("status")).isEqualTo("DISABLED");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(pinned(a, () -> jdbc.queryForObject(
                "select count(*) from devotee where created_by = ?", Integer.class, id))).isEqualTo(1);
        createUser(a, adminA, "leaver@x.example", "MEMBER"); // the email is free again
    }

    @Test
    void anotherTenantsStaffCantBeDeleted() throws Exception {
        MockHttpSession adminB = login(b, admin(b), PASSWORD);
        long theirs = userId(createUser(b, adminB, "victim@x.example", "MEMBER"));
        mvc.perform(on(a, delete("/api/v1/users/" + theirs)).session(adminA).with(csrf())).andExpect(status().isNotFound());
        assertThat(pinned(b, () -> jdbc.queryForObject("select count(*) from app_user where id = ?", Integer.class, theirs)))
                .isEqualTo(1);
    }

    // --- the last admin ----------------------------------------------------------------------

    @Test
    void theLastAdminCannotBeDemotedOrDeactivated() throws Exception {
        long me = meId(a, adminA);
        changeRole(a, adminA, me, "LEADER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("last_admin"));
        mvc.perform(on(a, post("/api/v1/users/" + me + "/deactivate")).session(adminA).with(csrf()))
                .andExpect(status().isConflict());

        onboard(a, adminA, "admin2@x.example", "TRUST_ADMIN");
        changeRole(a, adminA, me, "LEADER").andExpect(status().isOk());
    }

    /**
     * The last-admin check counts admins under a row lock, so two admins demoting each other at
     * the same moment are serialized: the second re-counts after the first commits. Tested
     * deterministically: while one transaction holds the lock, another can't take it. (An
     * end-to-end "race" test passed even without the lock; the requests never overlapped.)
     */
    @Test
    void adminsAreCountedUnderARowLock() throws Exception {
        onboard(a, adminA, "admin2@x.example", "TRUST_ADMIN");
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                pin(tenantA);
                assertThat(users.lockActiveAdmins()).hasSize(2);
                locked.countDown();
                await(release);
            }));
            locked.await();
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                pin(tenantA);
                jdbc.execute("set local lock_timeout = '300ms'");
                users.lockActiveAdmins();
            })).isInstanceOf(CannotAcquireLockException.class)
                    .rootCause().hasMessageContaining("lock timeout");
            release.countDown();
            holder.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // --- revocation --------------------------------------------------------------------------

    @Test
    void deactivationEndsTheUsersSessionOnTheirNextRequest() throws Exception {
        long id = onboard(a, adminA, "leaver@x.example", "LEADER");
        MockHttpSession leaver = loginAs(a, id);
        mvc.perform(on(a, get("/api/v1/me")).session(leaver)).andExpect(status().isOk());

        mvc.perform(on(a, post("/api/v1/users/" + id + "/deactivate")).session(adminA).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));

        mvc.perform(on(a, get("/api/v1/me")).session(leaver)).andExpect(status().isUnauthorized());
        login(a, "leaver@x.example", PASSWORD, 401);
    }

    @Test
    void aDemotionAppliesToTheUsersVeryNextRequest() throws Exception {
        long id = onboard(a, adminA, "admin2@x.example", "TRUST_ADMIN");
        MockHttpSession admin2 = loginAs(a, id);
        mvc.perform(on(a, get("/api/v1/users")).session(admin2)).andExpect(status().isOk());

        changeRole(a, adminA, id, "MEMBER").andExpect(status().isOk());

        mvc.perform(on(a, get("/api/v1/users")).session(admin2)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/me")).session(admin2)).andExpect(jsonPath("$.role").value("MEMBER"));
    }

    // --- helpers -----------------------------------------------------------------------------

    /** Creates a user as {@code admin}, completes their setup, returns their id. */
    private long onboard(String slug, MockHttpSession admin, String email, String role) throws Exception {
        JsonNode created = createUser(slug, admin, email, role);
        setup(slug, token(created.at("/setupUrl").asString()), PASSWORD).andExpect(status().isNoContent());
        return userId(created);
    }

    private JsonNode createUser(String slug, MockHttpSession admin, String email, String role) throws Exception {
        return json.readTree(mvc.perform(on(slug, post("/api/v1/users")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "displayName", "User", "role", role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions setup(String slug, String token, String password) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/setup")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("token", token, "password", password)));
    }

    private ResultActions changeRole(String slug, MockHttpSession session, long id, String role) throws Exception {
        return mvc.perform(on(slug, patch("/api/v1/users/" + id + "/role")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("role", role)));
    }

    private MockHttpSession loginAs(String slug, long userId) throws Exception {
        String email = pinned(slug, () -> jdbc.queryForObject("select email from app_user where id = ?", String.class, userId));
        return login(slug, email, PASSWORD);
    }

    private MockHttpSession login(String slug, String email, String password) throws Exception {
        return (MockHttpSession) login(slug, email, password, 200).andReturn().getRequest().getSession();
    }

    private ResultActions login(String slug, String email, String password, int expected) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .param("email", email).param("password", password))
                .andExpect(status().is(expected));
    }

    private long meId(String slug, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(on(slug, get("/api/v1/me")).session(session))
                .andReturn().getResponse().getContentAsString()).at("/userId").asLong();
    }

    /** Links that could still be redeemed (used ones stay as a record). */
    private Integer liveTokensFor(String slug, long userId) {
        return pinned(slug, () -> jdbc.queryForObject(
                "select count(*) from user_setup_token where user_id = ? and used_at is null", Integer.class, userId));
    }

    private void pin(long tenantId) {
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T pinned(String slug, java.util.function.Supplier<T> work) {
        long tenantId = tenants.findBySlug(slug).orElseThrow().getId();
        return tx.execute(s -> {
            jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
            return work.get();
        });
    }

    private static int statusOf(ResultActions result) {
        return result.andReturn().getResponse().getStatus();
    }

    private static long userId(JsonNode created) {
        return created.at("/user/id").asLong();
    }

    private static String token(String setupUrl) {
        return setupUrl.substring(setupUrl.indexOf("#token=") + "#token=".length());
    }

    private String body(String... keyValues) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    private static MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }

    private String register(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(slug, "Trust " + slug, admin(slug), PASSWORD, "Admin"));
        return slug;
    }

    private static String admin(String slug) {
        return "admin@" + slug + ".example";
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
