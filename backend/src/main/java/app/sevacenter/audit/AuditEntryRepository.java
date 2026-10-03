package app.sevacenter.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant; the app role can only read and insert. */
interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("select a from AuditEntry a where (?1 is null or a.action = ?1) order by a.createdAt desc, a.id desc")
    Page<AuditEntry> newestFirst(AuditAction action, Pageable page);
}
