package app.sevacenter.household;

import java.time.OffsetDateTime;

import app.sevacenter.portal.OtpChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An adult invited into a household; joins only by accepting with the invited contact (ADR 0039). RLS. */
@Entity
@Table(name = "household_invite")
public class HouseholdInvite {

    public enum Status { PENDING, ACCEPTED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "household_id", nullable = false)
    private Long householdId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpChannel channel;
    @Column(nullable = false)
    private String contact;
    @Column(nullable = false)
    private String relation;
    @Column(nullable = false)
    private String status = Status.PENDING.name();
    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected HouseholdInvite() { }

    HouseholdInvite(long tenantId, long householdId, OtpChannel channel, String contact, HouseholdMember.Relation relation,
                    long invitedBy, OffsetDateTime now, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.householdId = householdId;
        this.channel = channel;
        this.contact = contact;
        this.relation = relation.name();
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean open(OffsetDateTime now) {
        return getStatus() == Status.PENDING && now.isBefore(expiresAt);
    }

    void close(Status s) {
        this.status = s.name();
    }

    public Long getId() { return id; }
    public Long getHouseholdId() { return householdId; }
    public OtpChannel getChannel() { return channel; }
    public String getContact() { return contact; }
    public HouseholdMember.Relation getRelation() { return HouseholdMember.Relation.valueOf(relation); }
    public Status getStatus() { return Status.valueOf(status); }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
