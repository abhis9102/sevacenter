package app.sevacenter.household;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A family as a unit (ADR 0038): shared address and gotra; members are devotee records. RLS. */
@Entity
@Table(name = "household")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(nullable = false)
    private String name;
    private String gotra;
    @Column(name = "address_line")
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String notes;
    @Column(name = "created_by")
    private Long createdBy;
    /** Set instead of createdBy when a devotee started the household in My Mandir (ADR 0039). */
    @Column(name = "created_by_account")
    private Long createdByAccount;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Household() { }

    static Household startedByDevotee(long tenantId, long accountId, OffsetDateTime now) {
        Household h = new Household();
        h.tenantId = tenantId;
        h.createdByAccount = accountId;
        h.createdAt = now;
        return h;
    }

    Household(long tenantId, long staffId, OffsetDateTime now) {
        this.tenantId = tenantId;
        this.createdBy = staffId;
        this.createdAt = now;
    }

    void edit(String name, String gotra, String addressLine, String city, String state, String pincode, String notes,
              OffsetDateTime now) {
        this.name = name;
        this.gotra = gotra;
        this.addressLine = addressLine;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.notes = notes;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGotra() { return gotra; }
    public String getAddressLine() { return addressLine; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public String getNotes() { return notes; }
}
