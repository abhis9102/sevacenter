package app.sevacenter.temple;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS: at most the current tenant's row is ever visible. */
public interface TempleProfileRepository extends JpaRepository<TempleProfile, Long> {
}
