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

/** A phone or email a devotee proved they own, linked to their account (ADR 0025). RLS-scoped. */
@Entity
@Table(name = "devotee_contact")
public class DevoteeContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OtpChannel channel;

    @Column(nullable = false, updatable = false)
    private String contact;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    protected DevoteeContact() { }

    DevoteeContact(long tenantId, long accountId, OtpChannel channel, String contact, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.channel = channel;
        this.contact = contact;
        this.verifiedAt = now;
    }

    void moveTo(long accountId) {
        this.accountId = accountId;
    }

    void reverified(OffsetDateTime now) {
        this.verifiedAt = now;
    }

    public Long getAccountId() { return accountId; }
    public OtpChannel getChannel() { return channel; }
    public String getContact() { return contact; }
}
