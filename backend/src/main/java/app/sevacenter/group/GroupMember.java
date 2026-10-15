package app.sevacenter.group;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A devotee in a group: PENDING (asked to join an approval group) or ACTIVE. RLS. */
@Entity
@Table(name = "group_member")
public class GroupMember {

    public enum Status { PENDING, ACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(nullable = false)
    private String status;
    @Column(name = "added_by")
    private Long addedBy;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;
    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    protected GroupMember() { }

    GroupMember(long tenantId, long groupId, long accountId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.groupId = groupId;
        this.accountId = accountId;
        this.status = Status.PENDING.name();
        this.requestedAt = now;
    }

    void activate(Long staffId, OffsetDateTime now) {
        this.status = Status.ACTIVE.name();
        this.addedBy = staffId;
        this.joinedAt = now;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getAccountId() { return accountId; }
    public Status getStatus() { return Status.valueOf(status); }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
}
