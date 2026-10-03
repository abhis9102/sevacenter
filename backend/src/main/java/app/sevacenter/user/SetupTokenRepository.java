package app.sevacenter.user;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the request's tenant: a token can only be found on its own host. */
public interface SetupTokenRepository extends JpaRepository<SetupToken, Long> {

    /** Locked, so two concurrent redemptions of the same link can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SetupToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from SetupToken t where t.userId = :userId")
    void deleteAllForUser(Long userId);
}
