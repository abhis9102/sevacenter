package app.sevacenter.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.sevacenter.tenant.ReservedSlugs;
import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import app.sevacenter.user.Role;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Registers a new trust and its first TRUST_ADMIN.
 *
 * <p>Registration is tenant-agnostic (no tenant context on the way in). After creating the
 * tenant row (the {@code tenant} table has no RLS), we pin the RLS tenant to the new id so
 * the first user INSERT satisfies the {@code app_user} policy's WITH CHECK. The password is
 * stored only as a hash.
 */
@Service
public class RegistrationService {

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public RegistrationService(TenantRepository tenants, AppUserRepository users,
                               PasswordEncoder passwordEncoder) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest req) {
        String slug = req.slug().toLowerCase();
        // Reserved names answer exactly like taken ones: no hint about which list a name is on.
        if (ReservedSlugs.isReserved(slug) || tenants.existsBySlug(slug)) {
            throw new SlugAlreadyTakenException(slug);
        }

        Tenant tenant;
        try {
            tenant = tenants.saveAndFlush(new Tenant(slug, req.trustName().trim()));
        } catch (DataIntegrityViolationException e) {
            // Two registrations of one slug at once both pass the check; the unique index lets one win.
            throw new SlugAlreadyTakenException(slug);
        }

        // The one explicit pin: this transaction started with no tenant (TenantPinningDataSource
        // set ''), and the tenant it now writes into didn't exist until a moment ago.
        // Transaction-local (true), so the connection reverts to '' after commit.
        entityManager
                .createNativeQuery("select set_config('app.tenant_id', :tid, true)")
                .setParameter("tid", tenant.getId().toString())
                .getSingleResult();

        AppUser admin = new AppUser(
                tenant.getId(),
                req.adminEmail().toLowerCase(),
                passwordEncoder.encode(req.adminPassword()),
                req.adminName().trim(),
                Role.TRUST_ADMIN);
        users.save(admin);

        return new RegistrationResponse(
                slug,
                tenant.getId(),
                "https://" + slug + ".sevacenter.app",
                "https://" + slug + ".mandircenter.app");
    }
}
