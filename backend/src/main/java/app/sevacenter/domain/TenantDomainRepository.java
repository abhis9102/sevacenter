package app.sevacenter.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Hostname -> tenant for verified custom domains; see TenantDomain. */
public interface TenantDomainRepository extends JpaRepository<TenantDomain, String> {
}
