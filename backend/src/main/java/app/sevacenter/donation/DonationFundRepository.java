package app.sevacenter.donation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
public interface DonationFundRepository extends JpaRepository<DonationFund, Long> {

    @Query("select f from DonationFund f order by f.active desc, lower(f.name)")
    List<DonationFund> listed();

    @Query("select f from DonationFund f where f.active = true order by lower(f.name)")
    List<DonationFund> active();

    @Query("select count(f) > 0 from DonationFund f where lower(f.name) = lower(?1) and (?2 is null or f.id <> ?2)")
    boolean nameTaken(String name, Long exceptId);
}
