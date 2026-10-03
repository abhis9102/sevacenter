package app.sevacenter.donation;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Cancels a receipt whose donation was reversed. The receipt and its number stay; insert-only. */
@Entity
@Immutable
@Table(name = "receipt_cancellation")
public class ReceiptCancellation {

    @Id
    @Column(name = "receipt_id")
    private Long receiptId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "cancelled_by", nullable = false)
    private Long cancelledBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReceiptCancellation() { }

    ReceiptCancellation(long receiptId, long tenantId, String reason, long staffId) {
        this.receiptId = receiptId;
        this.tenantId = tenantId;
        this.reason = reason;
        this.cancelledBy = staffId;
    }

    public String getReason() { return reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
