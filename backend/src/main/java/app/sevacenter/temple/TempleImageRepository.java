package app.sevacenter.temple;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: at most the current tenant's row is ever visible. */
public interface TempleImageRepository extends JpaRepository<TempleImage, Long> {

    /** The version only, for the page payload, without loading the bytes. */
    @Query("select i.version from TempleImage i where i.tenantId = ?1")
    Optional<String> versionOf(long tenantId);
}
