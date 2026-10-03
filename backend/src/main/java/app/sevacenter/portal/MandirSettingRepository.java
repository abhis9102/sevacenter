package app.sevacenter.portal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MandirSettingRepository extends JpaRepository<MandirSetting, Long> {
    Optional<MandirSetting> findByTenantId(Long tenantId);
}
