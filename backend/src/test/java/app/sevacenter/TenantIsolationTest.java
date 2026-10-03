package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import app.sevacenter.auth.SlugAlreadyTakenException;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tenant isolation, proven against the real database as the application's own role.
 * Threat model invariants 1-3 (docs/security/threat-model-tenancy.md):
 * <ol>
 *   <li>tenant-scoped queries only ever see the tenant pinned for the transaction;</li>
 *   <li>the application's DB role cannot bypass Row-Level Security;</li>
 *   <li>tenant A can neither read nor write tenant B's rows.</li>
 * </ol>
 * The app connects as {@code sevacenter_app} (see {@link TestcontainersConfiguration}); a
 * superuser would bypass RLS and make all of this meaningless, so that is asserted first.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TenantIsolationTest {

    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    private long tenantA;
    private long tenantB;

    @BeforeEach
    void twoTenants() {
        tenantA = register("iso-a");
        tenantB = register("iso-b");
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // --- Invariant 2: the application's role cannot bypass RLS -------------------------------

    @Test
    void appConnectsAsLeastPrivilegeRoleThatRlsAppliesTo() {
        var role = jdbc.queryForMap(
                "select current_user as name, rolsuper, rolbypassrls from pg_roles where rolname = current_user");
        assertThat(role.get("name")).isEqualTo(TestcontainersConfiguration.APP_ROLE);
        assertThat(role.get("rolsuper")).isEqualTo(false);
        assertThat(role.get("rolbypassrls")).isEqualTo(false);
        assertThat(jdbc.queryForObject(
                "select tableowner from pg_tables where tablename = 'app_user'", String.class))
                .isNotEqualTo(TestcontainersConfiguration.APP_ROLE);
    }

    @Test
    void appRoleCannotSwitchRlsOff() {
        assertThatThrownBy(() -> jdbc.execute("alter table app_user disable row level security"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> jdbc.execute("alter table app_user no force row level security"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("must be owner");
    }

    /** Guards future tables: a new tenant-scoped table without RLS fails the build. */
    @Test
    void everyTenantScopedTableHasForcedRlsAndAPolicy() {
        List<String> unprotected = jdbc.queryForList("""
                select c.relname
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
                join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped
                where c.relkind = 'r'
                  and not (c.relrowsecurity and c.relforcerowsecurity
                           and exists (select 1 from pg_policy p where p.polrelid = c.oid))
                """, String.class);
        assertThat(unprotected).as("tenant-scoped tables without forced RLS + a policy").isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_attribute a join pg_class c on c.oid = a.attrelid "
                        + "where a.attname = 'tenant_id' and c.relkind = 'r'", Integer.class))
                .as("sanity: the catalog query sees tenant-scoped tables at all").isPositive();
    }

    // --- Invariants 1 + 3 at the database layer ----------------------------------------------

    @Test
    void noTenantPinnedSeesNothing() {
        Integer visible = tx.execute(s -> count("select count(*) from app_user"));
        assertThat(visible).isZero();
    }

    @Test
    void pinnedTenantSeesOnlyItsOwnRows() {
        tx.executeWithoutResult(s -> {
            pin(tenantA);
            assertThat(count("select count(*) from app_user where tenant_id = " + tenantA)).isPositive();
            assertThat(count("select count(*) from app_user where tenant_id <> " + tenantA)).isZero();
        });
    }

    @Test
    void tenantCannotInsertIntoAnotherTenant() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            pin(tenantA);
            jdbc.update("insert into app_user (tenant_id, email, password_hash, display_name, role) "
                    + "values (?, 'intruder@example.org', 'x', 'Intruder', 'TRUST_ADMIN')", tenantB);
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void tenantCannotUpdateOrDeleteAnotherTenantsRows() {
        tx.executeWithoutResult(s -> {
            pin(tenantA);
            assertThat(jdbc.update("update app_user set display_name = 'pwned' where tenant_id = ?", tenantB))
                    .isZero();
            assertThat(jdbc.update("delete from app_user where tenant_id = ?", tenantB)).isZero();
        });
        tx.executeWithoutResult(s -> {
            pin(tenantB);
            assertThat(count("select count(*) from app_user where display_name = 'pwned'")).isZero();
            assertThat(count("select count(*) from app_user where tenant_id = " + tenantB)).isPositive();
        });
    }

    @Test
    void tenantPinDoesNotLeakIntoTheNextTransaction() {
        // set_config(..., true) is transaction-local; pooled connections must come back clean.
        for (int i = 0; i < 5; i++) {
            tx.executeWithoutResult(s -> pin(tenantA));
            Integer visible = tx.execute(s -> count("select count(*) from app_user"));
            assertThat(visible).isZero();
        }
    }

    // --- Invariants 1 + 3 through the application path ---------------------------------------

    @Test
    void repositoriesOnlySeeTheTenantInContext() {
        long bUserId = tx.execute(s -> {
            pin(tenantB);
            return jdbc.queryForObject("select id from app_user where tenant_id = ?", Long.class, tenantB);
        });

        TenantContext.set(tenantA);
        List<AppUser> visible = users.findAll();
        assertThat(visible).isNotEmpty().allMatch(u -> u.getTenantId() == tenantA);
        assertThat(users.findById(bUserId)).as("BOLA: B's user by id, from A").isEmpty();

        TenantContext.clear();
        assertThat(users.findAll()).as("no tenant in context: fail closed").isEmpty();
    }

    // --- Invariant 4: reserved subdomains can't be registered ---------------------------------

    @Test
    void reservedSubdomainsCannotBeRegistered() {
        for (String reserved : List.of("admin", "api", "www", "app", "mail", "register", "signup", "password")) {
            assertThatThrownBy(() -> registration.register(new RegistrationRequest(
                    reserved, "Impostor", "x@" + reserved + ".example", "correct-horse-battery-staple", "X")))
                    .as(reserved).isInstanceOf(SlugAlreadyTakenException.class);
            assertThat(tenants.findBySlug(reserved)).as(reserved).isEmpty();
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private long register(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(
                slug, "Trust " + slug, "admin@" + slug + ".example", "correct-horse-battery-staple", "Admin"));
        return tenants.findBySlug(slug).orElseThrow().getId();
    }

    private void pin(long tenantId) {
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
