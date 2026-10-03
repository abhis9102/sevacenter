package app.sevacenter.donation;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS scopes it to the current tenant: at most one row is ever visible. */
public interface TrustProfileRepository extends JpaRepository<TrustProfile, Long> {
}
