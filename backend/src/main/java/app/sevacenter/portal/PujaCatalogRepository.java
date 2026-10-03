package app.sevacenter.portal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PujaCatalogRepository extends JpaRepository<PujaCatalogItem, Long> {
    List<PujaCatalogItem> findAllByTenantIdOrderByDisplayOrderAsc(Long tenantId);
    List<PujaCatalogItem> findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc(Long tenantId);
    Optional<PujaCatalogItem> findByTenantIdAndCode(Long tenantId, String code);
}
