package app.sevacenter.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS: only the current tenant's claims are ever visible. */
public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

    List<CustomDomain> findAllByOrderByCreatedAtAsc();

    Optional<CustomDomain> findByHostname(String hostname);

    List<CustomDomain> findAllByStatus(String status);

    long count();
}
