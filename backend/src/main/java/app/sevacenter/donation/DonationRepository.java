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

    @Query("select d.fundId as fundId, f.name as fundName, sum(d.amountPaise) as netPaise, "
            + "sum(case when d.reversesId is null then 1 else 0 end) as donations "
            + "from Donation d left join DonationFund f on f.id = d.fundId "
            + "where d.receivedOn between ?1 and ?2 group by d.fundId, f.name order by f.name nulls first")
    List<FundTotal> totalsByFund(LocalDate from, LocalDate to);

    interface FundTotal {
        Long getFundId();
        String getFundName();
        Long getNetPaise();
        Long getDonations();
    }

    /**
     * A verified contact's own donations (ADR 0019): online ones by the contact the donor left,
     * staff-recorded ones through the linked devotee's phone/email. Exact matches only.
     */
    @Query(MINE + " order by d.receivedOn desc, d.id desc")
    List<Donation> forContact(String contact, Pageable page);

    @Query("select count(d) > 0 " + MINE_FROM + " and d.id = ?2")
    boolean isFor(String contact, Long donationId);

    @Query("select r.reversesId from Donation r where r.reversesId in ?1")
    List<Long> reversedAmong(List<Long> donationIds);

    String MINE_FROM = "from Donation d where d.reversesId is null and ("
            + "d.devoteeId in (select v.id from Devotee v where v.phone = ?1 or v.email = ?1) or "
            + "d.id in (select i.donationId from PaymentIntent i where i.donorPhone = ?1 or i.donorEmail = ?1))";
    String MINE = "select d " + MINE_FROM;

    interface ModeTotal {
        DonationMode getMode();
        Long getNetPaise();
        Long getDonations();
        Long getReversals();
    }
}
