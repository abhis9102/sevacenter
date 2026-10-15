package app.sevacenter.household;

import java.util.List;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.devotee.Devotee;
import app.sevacenter.donation.Money;
import app.sevacenter.portal.DevoteeAccount;
import app.sevacenter.portal.DevoteeContact;
import app.sevacenter.portal.DevoteeLoginService;
import app.sevacenter.portal.PortalController;
import app.sevacenter.user.ModuleAccess;
import app.sevacenter.user.Role;
import app.sevacenter.user.StaffModule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Households (ADR 0038). Staff (Devotees module): MEMBER+ see households with members' names (the
 * shared address only LEADER+, like devotee records); LEADER+ create and change them; family
 * giving only for staff who can see donations. Devotees: their own household, read-only, names
 * and relations only.
 */
@RestController
public class HouseholdController {

    private final HouseholdService service;
    private final DevoteeLoginService login;

    public HouseholdController(HouseholdService service, DevoteeLoginService login) {
        this.service = service;
        this.login = login;
    }

    // --- staff ------------------------------------------------------------------------------

    @GetMapping("/api/v1/households")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public List<HouseholdSummary> search(@RequestParam(required = false) String q) {
        return service.search(q).stream().map(h -> {
            List<HouseholdService.MemberView> people = service.members(h.getId());
            String head = people.stream().filter(m -> m.member().getRelation() == HouseholdMember.Relation.HEAD)
                    .map(m -> m.devotee().getFullName()).findFirst().orElse(null);
            return new HouseholdSummary(h.getId(), h.getName(), h.getGotra(), head, people.size());
        }).toList();
    }

    @GetMapping("/api/v1/households/{id:\\d+}")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public ResponseEntity<HouseholdResponse> get(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(service.get(id), staff));
    }

    /** The household a devotee belongs to; 404 if none. */
    @GetMapping("/api/v1/devotees/{id:\\d+}/household")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public ResponseEntity<HouseholdResponse> ofDevotee(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return service.householdOf(id).map(h -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(service.get(h), staff))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/households")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public HouseholdResponse create(@Valid @RequestBody CreateRequest r, @AuthenticationPrincipal StaffUser staff) {
        Household h = service.create(r.household().details(), r.devoteeId(), r.relation(), staff.userId());
        return view(h, staff);
    }

    @PutMapping("/api/v1/households/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @Transactional
    public HouseholdResponse update(@PathVariable long id, @Valid @RequestBody HouseholdRequest r,
                                    @AuthenticationPrincipal StaffUser staff) {
        return view(service.update(id, r.details()), staff);
    }

    @DeleteMapping("/api/v1/households/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/api/v1/households/{id:\\d+}/members")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable long id, @Valid @RequestBody MemberRequest r) {
        service.addMember(id, r.devoteeId(), r.relation());
    }

    @PutMapping("/api/v1/households/{id:\\d+}/members/{memberId:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public void setRelation(@PathVariable long id, @PathVariable long memberId, @Valid @RequestBody RelationRequest r) {
        service.setRelation(id, memberId, r.relation());
    }

    @DeleteMapping("/api/v1/households/{id:\\d+}/members/{memberId:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable long id, @PathVariable long memberId) {
        service.removeMember(id, memberId);
    }

    // --- devotee ----------------------------------------------------------------------------

    /** The signed-in devotee's family, through their verified contacts. Names and relations only. */
    @GetMapping("/api/v1/portal/household")
    @Transactional(readOnly = true)
    public ResponseEntity<MyHousehold> mine(@CookieValue(name = PortalController.COOKIE, required = false) String token) {
        DevoteeAccount me = login.resolve(token).orElseThrow(PortalController.NotLoggedInException::new);
        List<String> contacts = login.contactsOf(me).stream().map(DevoteeContact::getContact).toList();
        return service.forContacts(contacts)
                .map(h -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MyHousehold(h.getName(),
                        h.getGotra(), service.members(h.getId()).stream()
                        .map(m -> new FamilyMember(m.devotee().getFullName(), m.member().getRelation())).toList())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // --- payloads ---------------------------------------------------------------------------

    private HouseholdResponse view(Household h, StaffUser staff) {
        boolean full = staff.role() != Role.MEMBER;
        boolean money = full && staff.access(StaffModule.DONATIONS) != ModuleAccess.NONE;
        List<MemberResponse> people = service.members(h.getId()).stream().map(m -> {
            Devotee d = m.devotee();
            return new MemberResponse(m.member().getId(), d.getId(), d.getFullName(), m.member().getRelation());
        }).toList();
        return new HouseholdResponse(h.getId(), h.getName(), h.getGotra(),
                full ? h.getAddressLine() : null, full ? h.getCity() : null, full ? h.getState() : null,
                full ? h.getPincode() : null, full ? h.getNotes() : null, people,
                money ? Money.toRupees(service.givingThisYear(h.getId())) : null, !full);
    }

    public record HouseholdRequest(
            @Schema(example = "Kulkarni family") @NotBlank @Size(max = 120) String name,
            @Schema(example = "Kashyap") @Size(max = 60) String gotra,
            @Schema(example = "12 Temple Street") @Size(max = 200) String addressLine,
            @Schema(example = "Pune") @Size(max = 80) String city,
            @Schema(example = "Maharashtra") @Size(max = 80) String state,
            @Schema(example = "411001") @Size(max = 6) String pincode,
            @Schema(example = "Prefers Marathi") @Size(max = 500) String notes) {
        HouseholdService.Details details() {
            return new HouseholdService.Details(name, gotra, addressLine, city, state, pincode, notes);
        }
    }

    public record CreateRequest(@NotNull @Valid HouseholdRequest household,
                                @Schema(example = "1") @NotNull Long devoteeId,
                                @Schema(example = "HEAD") @NotNull HouseholdMember.Relation relation) { }

    public record MemberRequest(@Schema(example = "1") @NotNull Long devoteeId,
                                @Schema(example = "SPOUSE") @NotNull HouseholdMember.Relation relation) { }

    public record RelationRequest(@Schema(example = "CHILD") @NotNull HouseholdMember.Relation relation) { }

    public record HouseholdSummary(long id, String name, String gotra, String head, int members) { }

    public record MemberResponse(long memberId, long devoteeId, String fullName, HouseholdMember.Relation relation) { }

    public record HouseholdResponse(long id, String name, String gotra, String addressLine, String city, String state,
                                    String pincode, String notes, List<MemberResponse> members,
                                    @Schema(description = "Net giving this financial year; null without donations access")
                                    String givingThisYear,
                                    boolean masked) { }

    public record FamilyMember(String name, HouseholdMember.Relation relation) { }

    public record MyHousehold(String name, String gotra, List<FamilyMember> members) { }
}
