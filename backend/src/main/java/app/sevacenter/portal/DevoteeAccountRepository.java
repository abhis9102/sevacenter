package app.sevacenter.portal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS scopes every query to the current tenant. */
interface DevoteeAccountRepository extends JpaRepository<DevoteeAccount, Long> {

    Optional<DevoteeAccount> findByChannelAndContact(OtpChannel channel, String contact);

    List<DevoteeAccount> findByMergedInto(Long accountId);
}
