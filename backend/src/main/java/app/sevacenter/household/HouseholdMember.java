package app.sevacenter.household;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A devotee's place in their household (at most one household each). RLS. */
@Entity
@Table(name = "household_member")
public class HouseholdMember {

    public enum Relation { HEAD, SPOUSE, CHILD, PARENT, SIBLING, GRANDPARENT, GRANDCHILD, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "household_id", nullable = false)
    private Long householdId;
    @Column(name = "devotee_id", nullable = false)
    private Long devoteeId;
    @Column(nullable = false)
    private String relation;
    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    protected HouseholdMember() { }

    HouseholdMember(long tenantId, long householdId, long devoteeId, Relation relation, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.householdId = householdId;
        this.devoteeId = devoteeId;
        this.relation = relation.name();
        this.addedAt = now;
    }

    void relation(Relation r) {
        this.relation = r.name();
    }

    public Long getId() { return id; }
    public Long getHouseholdId() { return householdId; }
    public Long getDevoteeId() { return devoteeId; }
    public Relation getRelation() { return Relation.valueOf(relation); }
}
