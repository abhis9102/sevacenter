package app.sevacenter.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Queries never mention tenant_id — Row-Level Security scopes every result to the current
 * tenant automatically. {@code findByEmail} therefore finds a user only within the active
 * tenant, which is exactly what per-tenant unique emails require.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

    Optional<AppUser> findByIdAndDeletedAtIsNull(Long id);

    /**
     * The tenant's active admins, row-locked: demoting or disabling an admin re-counts under the
     * lock, so two admins demoting each other at the same moment can't leave the trust with none.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.role = app.sevacenter.user.Role.TRUST_ADMIN "
            + "and u.status = app.sevacenter.user.UserStatus.ACTIVE")
    List<AppUser> lockActiveAdmins();
}
