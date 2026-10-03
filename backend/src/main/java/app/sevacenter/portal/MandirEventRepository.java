package app.sevacenter.portal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MandirEventRepository extends JpaRepository<MandirEventEntity, Long> {
    List<MandirEventEntity> findAllByTenantIdOrderByEventDateAsc(Long tenantId);
    List<MandirEventEntity> findAllByTenantIdAndActiveTrueOrderByEventDateAsc(Long tenantId);
    Optional<MandirEventEntity> findByTenantIdAndCode(Long tenantId, String code);
}
