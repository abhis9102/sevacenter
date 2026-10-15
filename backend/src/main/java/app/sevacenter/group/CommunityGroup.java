package app.sevacenter.group;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A satsang, bhajan mandali, youth group, study circle… (ADR 0037). RLS. */
@Entity
@Table(name = "community_group")
public class CommunityGroup {

    public enum Category { SATSANG, BHAJAN, YOUTH, WOMEN, STUDY, SEVA, OTHER }
    public enum Visibility { PUBLIC, PRIVATE }
    public enum JoinPolicy { OPEN, APPROVAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(nullable = false)
    private String category;
    private String meets;
    private String location;
    private Integer capacity;
    @Column(nullable = false)
    private String visibility;
    @Column(name = "join_policy", nullable = false)
    private String joinPolicy;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CommunityGroup() { }

    CommunityGroup(long tenantId, long staffId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.createdBy = staffId;
        this.createdAt = now;
    }

    void edit(String name, String description, Category category, String meets, String location, Integer capacity,
              Visibility visibility, JoinPolicy joinPolicy, boolean active, OffsetDateTime now) {
        this.name = name;
        this.description = description;
        this.category = category.name();
        this.meets = meets;
        this.location = location;
        this.capacity = capacity;
        this.visibility = visibility.name();
        this.joinPolicy = joinPolicy.name();
        this.active = active;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return Category.valueOf(category); }
    public String getMeets() { return meets; }
    public String getLocation() { return location; }
    public Integer getCapacity() { return capacity; }
    public Visibility getVisibility() { return Visibility.valueOf(visibility); }
    public JoinPolicy getJoinPolicy() { return JoinPolicy.valueOf(joinPolicy); }
    public boolean isActive() { return active; }

    /** Listed on MandirCenter and joinable by devotees themselves. */
    public boolean isListed() {
        return active && getVisibility() == Visibility.PUBLIC;
    }
}
