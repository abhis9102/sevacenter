package app.sevacenter.portal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DevoteeSessionRepository extends JpaRepository<DevoteeSession, Long> {

    Optional<DevoteeSession> findByTenantIdAndSessionTokenHash(Long tenantId, String sessionTokenHash);

    void deleteByTenantIdAndSessionTokenHash(Long tenantId, String sessionTokenHash);
}
