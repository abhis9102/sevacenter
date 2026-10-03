package app.sevacenter.devotee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * RLS scopes every query to the current tenant. Search terms go through Spring Data's
 * {@code escape()}, so {@code %} and {@code _} typed by a user match literally instead of
 * becoming wildcards.
 */
public interface DevoteeRepository extends JpaRepository<Devotee, Long> {

    @Query("select d from Devotee d where lower(d.fullName) like lower(concat('%', :#{escape([0])}, '%')) "
            + "escape :#{escapeCharacter()} and d.erasedAt is null")
    Page<Devotee> searchByName(String q, Pageable pageable);

    /** Name, phone or email: only for roles that see contact details in full (ADR 0010). */
    @Query("select d from Devotee d where (lower(d.fullName) like lower(concat('%', :#{escape([0])}, '%')) "
            + "escape :#{escapeCharacter()} "
            + "or d.phone like concat('%', :#{escape([0])}, '%') escape :#{escapeCharacter()} "
            + "or lower(d.email) like lower(concat('%', :#{escape([0])}, '%')) escape :#{escapeCharacter()}) "
            + "and d.erasedAt is null")
    Page<Devotee> searchAll(String q, Pageable pageable);

    java.util.Optional<Devotee> findByIdAndErasedAtIsNull(Long id);

    java.util.List<Devotee> findAllByErasedAtIsNull(org.springframework.data.domain.Sort sort);

    /** Whether the ledger references this devotee (then erasure anonymises instead of deleting). */
    @Query(value = "select exists (select 1 from donation where devotee_id = ?1)", nativeQuery = true)
    boolean hasDonations(long devoteeId);
}
