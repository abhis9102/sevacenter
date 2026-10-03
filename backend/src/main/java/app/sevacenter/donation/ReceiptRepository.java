package app.sevacenter.donation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** RLS scopes every query to the current tenant; the role can only read and insert (V8). */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByDonationId(Long donationId);

    Page<Receipt> findByFyStart(int fyStart, Pageable pageable);

    /** Receipts issued to the same PAN, matched by blind index (no decryption). */
    List<Receipt> findByDonorPanIndex(String donorPanIndex);

    /**
     * Takes the next receipt number for (tenant, FY) atomically: the upsert locks the counter row
     * until this transaction ends, so concurrent issuers serialize, and a rollback hands the number
     * back. Gapless and unique; UNIQUE (tenant_id, fy_start, seq) is the backstop.
     */
    @Query(value = "insert into receipt_counter (tenant_id, fy_start, next_seq) values (?1, ?2, 2) "
            + "on conflict (tenant_id, fy_start) do update set next_seq = receipt_counter.next_seq + 1 "
            + "returning next_seq - 1", nativeQuery = true)
    int takeNumber(long tenantId, int fyStart);
}
