package app.sevacenter.portal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant: a session only resolves on its own trust's host. */
interface DevoteeSessionRepository extends JpaRepository<DevoteeSession, Long> {

    Optional<DevoteeSession> findByTokenHash(String tokenHash);

    @Query("select s from DevoteeSession s where s.accountId = ?1 and s.revokedAt is null")
    List<DevoteeSession> openOf(long accountId);
}
