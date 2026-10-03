package app.sevacenter.puja;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface PujaBookingRepository extends JpaRepository<PujaBooking, Long> {

    @Query("select b from PujaBooking b where b.pujaDate = ?1 and b.status <> 'AWAITING_PAYMENT' order by b.createdAt")
    List<PujaBooking> forDate(LocalDate date);

    /** Locked: settlement and staff actions on one booking serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PujaBooking b where b.id = ?1")
    Optional<PujaBooking> lockById(Long id);

    boolean existsByBookingCode(String code);

    /** A verified devotee's own bookings (ADR 0018); unpaid attempts aren't bookings yet. */
    @Query("select b from PujaBooking b where (b.phone = ?1 or b.email = ?1) and b.status <> 'AWAITING_PAYMENT' "
            + "order by b.pujaDate desc")
    List<PujaBooking> forContact(String contact, org.springframework.data.domain.Pageable page);
}
