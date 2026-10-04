package app.sevacenter.portal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
interface DevoteeContactRepository extends JpaRepository<DevoteeContact, Long> {

    Optional<DevoteeContact> findByChannelAndContact(OtpChannel channel, String contact);

    @Query("select c from DevoteeContact c where c.accountId = ?1 order by c.channel, c.id")
    List<DevoteeContact> ofAccount(long accountId);

    long countByAccountId(long accountId);
}
