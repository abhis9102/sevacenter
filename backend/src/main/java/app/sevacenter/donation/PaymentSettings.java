package app.sevacenter.donation;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A trust's own gateway credentials (ADR 0013). The secret only as SecretBox ciphertext. */
@Entity
@Table(name = "payment_settings")
public class PaymentSettings {

    static final String SECRET_PURPOSE = "razorpay_key_secret";

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false)
    private String provider = "RAZORPAY";

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "key_secret_enc", nullable = false)
    private String keySecretEnc;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PaymentSettings() { }

    PaymentSettings(long tenantId) {
        this.tenantId = tenantId;
    }

    void replace(String keyId, String keySecretEnc, long staffId, OffsetDateTime now) {
        this.keyId = keyId;
        this.keySecretEnc = keySecretEnc;
        this.updatedBy = staffId;
        this.updatedAt = now;
    }

    public Long getTenantId() { return tenantId; }
    public String getKeyId() { return keyId; }
    String getKeySecretEnc() { return keySecretEnc; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
