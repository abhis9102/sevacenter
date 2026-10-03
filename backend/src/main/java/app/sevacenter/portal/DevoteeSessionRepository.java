package app.sevacenter.portal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS scopes every query to the current tenant: a session only resolves on its own trust's host. */
interface DevoteeSessionRepository extends JpaRepository<DevoteeSession, Long> {

    Optional<DevoteeSession> findByTokenHash(String tokenHash);
}
