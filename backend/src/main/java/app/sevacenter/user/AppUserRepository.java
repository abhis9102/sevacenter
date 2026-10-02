package app.sevacenter.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Queries never mention tenant_id — Row-Level Security scopes every result to the current
 * tenant automatically. {@code findByEmail} therefore finds a user only within the active
 * tenant, which is exactly what per-tenant unique emails require.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findAllByOrderByCreatedAtAsc();
}
