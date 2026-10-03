package app.sevacenter.donation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant: an order id only resolves on its own host. */
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    /** Locked: a confirm and a reconcile (or two confirms) for one order settle it exactly once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentIntent i where i.razorpayOrderId = ?1")
    Optional<PaymentIntent> lockByOrderId(String orderId);

    @Query("select i from PaymentIntent i where i.status = 'CREATED' and i.createdAt < ?1 order by i.createdAt")
    List<PaymentIntent> pendingBefore(OffsetDateTime cutoff, Pageable page);
}
