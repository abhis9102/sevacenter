package app.sevacenter.audit;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One audit log row (ADR 0020). Immutable: every column is insert-only; the DB grants no UPDATE. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "target_type", updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(updatable = false)
    private String detail;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditEntry() { }

    AuditEntry(long tenantId, Long actorId, AuditAction action, String targetType, Long targetId, String detail) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public AuditAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getDetail() { return detail; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
