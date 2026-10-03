package app.sevacenter.portal;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One login code sent (ADR 0018): only MACs of the contact and the code are stored. RLS-scoped. */
@Entity
@Table(name = "devotee_otp")
class DevoteeOtp {

    static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OtpChannel channel;

    @Column(name = "contact_mac", nullable = false, updatable = false)
    private String contactMac;

    @Column(name = "code_mac", nullable = false, updatable = false)
    private String codeMac;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DevoteeOtp() { }

    DevoteeOtp(long tenantId, OtpChannel channel, String contactMac, String codeMac, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.channel = channel;
        this.contactMac = contactMac;
        this.codeMac = codeMac;
        this.expiresAt = expiresAt;
    }

    /** Still worth checking a guess against: unused, unexpired, attempts left. */
    boolean open(OffsetDateTime now) {
        return usedAt == null && now.isBefore(expiresAt) && attempts < MAX_ATTEMPTS;
    }

    void countAttempt() {
        attempts++;
    }

    void use(OffsetDateTime now) {
        usedAt = now;
    }

    Long getId() { return id; }
    String codeMac() { return codeMac; }
}
