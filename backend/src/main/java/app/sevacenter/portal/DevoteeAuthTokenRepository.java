package app.sevacenter.portal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DevoteeAuthTokenRepository extends JpaRepository<DevoteeAuthToken, Long> {

    Optional<DevoteeAuthToken> findTopByTenantIdAndTargetTypeAndTargetValueAndConsumedAtIsNullOrderByCreatedAtDesc(
            Long tenantId, String targetType, String targetValue);
}
