package app.sevacenter.household;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS: only the current tenant's households. */
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    @Query("select h from Household h where lower(h.name) like lower(concat('%', ?1, '%')) order by h.name")
    List<Household> search(String q, Pageable page);

    /** Net giving (donations minus reversals) of the household's members between two dates, in paise. */
    @Query(value = "select coalesce(sum(d.amount_paise), 0) from donation d join household_member m "
            + "on m.devotee_id = d.devotee_id where m.household_id = ?1 and d.received_on between ?2 and ?3",
            nativeQuery = true)
    long netGivingPaise(long householdId, LocalDate from, LocalDate to);
}
