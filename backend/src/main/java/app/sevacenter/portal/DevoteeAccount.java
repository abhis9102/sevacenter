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

/** A contact a devotee proved they own by entering a code sent to it (ADR 0018). RLS-scoped. */
@Entity
@Table(name = "devotee_account")
public class DevoteeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OtpChannel channel;

    @Column(nullable = false, updatable = false)
    private String contact;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private OffsetDateTime verifiedAt;

    @Column(name = "last_login_at", nullable = false)
    private OffsetDateTime lastLoginAt;

    protected DevoteeAccount() { }

    DevoteeAccount(long tenantId, OtpChannel channel, String contact, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.channel = channel;
        this.contact = contact;
        this.verifiedAt = now;
        this.lastLoginAt = now;
    }

    void loggedIn(OffsetDateTime now) {
        lastLoginAt = now;
    }

    public Long getId() { return id; }
    public OtpChannel getChannel() { return channel; }
    public String getContact() { return contact; }
}
