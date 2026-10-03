package app.sevacenter.donation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant; the role can only read and insert (V6). */
public interface DonationRepository extends JpaRepository<Donation, Long> {

    @Query("select d from Donation d where d.receivedOn between ?1 and ?2 and (?3 is null or d.devoteeId = ?3)")
    Page<Donation> search(LocalDate from, LocalDate to, Long devoteeId, Pageable pageable);

    boolean existsByReversesId(Long donationId);

    boolean existsByDevoteeId(Long devoteeId);

    /** Net totals per mode for a date range: reversals are negative, so they net out. */
    @Query("select d.mode as mode, sum(d.amountPaise) as netPaise, "
            + "sum(case when d.reversesId is null then 1 else 0 end) as donations, "
            + "sum(case when d.reversesId is null then 0 else 1 end) as reversals "
            + "from Donation d where d.receivedOn between ?1 and ?2 group by d.mode order by d.mode")
    List<ModeTotal> totalsByMode(LocalDate from, LocalDate to);

    interface ModeTotal {
        DonationMode getMode();
        Long getNetPaise();
        Long getDonations();
        Long getReversals();
    }
}
