package app.sevacenter.portal;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant. */
interface DevoteeOtpRepository extends JpaRepository<DevoteeOtp, Long> {

    long countByContactMacAndCreatedAtAfter(String contactMac, OffsetDateTime since);

    long countByChannelAndCreatedAtAfter(OtpChannel channel, OffsetDateTime since);

    /**
     * The newest code only: an earlier one is dead the moment another is sent. Locked: concurrent
     * guesses at one code count one by one, so the attempt cap holds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DevoteeOtp o where o.contactMac = ?1 order by o.id desc")
    List<DevoteeOtp> lockLatest(String contactMac, Pageable page);

}
