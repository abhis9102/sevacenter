package app.sevacenter.sevak;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface SevakSignupRepository extends JpaRepository<SevakSignup, Long> {

    @Query("select s from SevakSignup s where s.removedAt is null order by s.createdAt desc")
    List<SevakSignup> newestFirst(Pageable page);

    /** A verified devotee's own signups (ADR 0018). */
    @Query("select s from SevakSignup s where (s.phone = ?1 or s.email = ?1) and s.removedAt is null order by s.createdAt desc")
    List<SevakSignup> forContact(String contact, Pageable page);

    @Query("select s from SevakSignup s where s.teamId = ?1 and s.removedAt is null")
    List<SevakSignup> inTeam(long teamId);
}
