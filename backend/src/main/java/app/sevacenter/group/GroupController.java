package app.sevacenter.group;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.portal.DevoteeAccount;
import app.sevacenter.portal.DevoteeLoginService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.portal.PortalController;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Groups (ADR 0037). Staff: /api/v1/groups (MEMBER+ reads the list, LEADER+ runs groups and sees
 * members). Public: /api/v1/public/groups (listed groups, member counts only). Devotees:
 * /api/v1/portal/groups (their own memberships; join/leave), signed in with the devotee cookie.
 */
@RestController
public class GroupController {

    private final GroupService service;
    private final DevoteeLoginService login;

    public GroupController(GroupService service, DevoteeLoginService login) {
        this.service = service;
        this.login = login;
    }

    // --- staff ------------------------------------------------------------------------------

    @GetMapping("/api/v1/groups")
    @PreAuthorize("hasRole('MEMBER')")
    public List<GroupResponse> list() {
        Map<Long, Long> counts = service.activeCounts();
        return service.all().stream().map(g -> GroupResponse.of(g, counts.getOrDefault(g.getId(), 0L))).toList();
    }

    @PostMapping("/api/v1/groups")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody GroupRequest r, @AuthenticationPrincipal StaffUser staff) {
        return GroupResponse.of(service.create(r.details(), staff.userId()), 0);
    }

    @PutMapping("/api/v1/groups/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public GroupResponse update(@PathVariable long id, @Valid @RequestBody GroupRequest r) {
        return GroupResponse.of(service.update(id, r.details()), service.activeCounts().getOrDefault(id, 0L));
    }

    @DeleteMapping("/api/v1/groups/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /** Names and contacts: LEADER+ only (members of a group are personal data). */
    @GetMapping("/api/v1/groups/{id:\\d+}/members")
    @PreAuthorize("hasRole('LEADER')")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.members(id).stream().map(MemberResponse::of).toList());
    }

    @PostMapping("/api/v1/groups/{id:\\d+}/members")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(@PathVariable long id, @Valid @RequestBody AddMember r,
                                   @AuthenticationPrincipal StaffUser staff) {
        return Map.of("memberId", service.add(id, r.channel(), r.contact(), staff.userId()).getId());
    }

    @PostMapping("/api/v1/groups/{id:\\d+}/members/{memberId:\\d+}/approve")
    @PreAuthorize("hasRole('LEADER')")
    public Map<String, Object> approve(@PathVariable long id, @PathVariable long memberId,
                                       @AuthenticationPrincipal StaffUser staff) {
        return Map.of("status", service.approve(id, memberId, staff.userId()).getStatus());
    }

    @DeleteMapping("/api/v1/groups/{id:\\d+}/members/{memberId:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, @PathVariable long memberId) {
        service.remove(id, memberId);
    }

    // --- public -----------------------------------------------------------------------------

    @GetMapping("/api/v1/public/groups")
    public List<PublicGroup> listed() {
        Map<Long, Long> counts = service.activeCounts();
        return service.listed().stream().map(g -> PublicGroup.of(g, counts.getOrDefault(g.getId(), 0L))).toList();
    }

    // --- devotee ----------------------------------------------------------------------------

    @GetMapping("/api/v1/portal/groups")
    public ResponseEntity<List<MyGroup>> mine(@CookieValue(name = PortalController.COOKIE, required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.mine(devotee(token)).stream().map(MyGroup::of).toList());
    }

    @PostMapping("/api/v1/portal/groups/{id:\\d+}/join")
    public MyGroup join(@PathVariable long id, @CookieValue(name = PortalController.COOKIE, required = false) String token) {
        DevoteeAccount me = devotee(token);
        GroupMember m = service.join(id, me);
        return service.mine(me).stream().filter(x -> x.member().getId().equals(m.getId())).findFirst()
                .map(MyGroup::of).orElseThrow(GroupService.NotFoundException::new);
    }

    @DeleteMapping("/api/v1/portal/groups/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable long id, @CookieValue(name = PortalController.COOKIE, required = false) String token) {
        service.leave(id, devotee(token));
    }

    private DevoteeAccount devotee(String token) {
        return login.resolve(token).orElseThrow(PortalController.NotLoggedInException::new);
    }

    // --- payloads ---------------------------------------------------------------------------

    public record GroupRequest(
            @Schema(example = "Sunday Satsang") @NotBlank @Size(max = 80) String name,
            @Schema(example = "Bhajans and a short pravachan, all welcome") @Size(max = 1000) String description,
            @Schema(example = "SATSANG") @NotNull CommunityGroup.Category category,
            @Schema(example = "Every Sunday, 6:00 pm") @Size(max = 200) String meets,
            @Schema(example = "Temple hall, first floor") @Size(max = 200) String location,
            @Schema(example = "40") @Min(1) @Max(10000) Integer capacity,
            @Schema(example = "PUBLIC") @NotNull CommunityGroup.Visibility visibility,
            @Schema(example = "OPEN") @NotNull CommunityGroup.JoinPolicy joinPolicy,
            Boolean active) {
        GroupService.Details details() {
            return new GroupService.Details(name, description, category, meets, location, capacity, visibility,
                    joinPolicy, active == null || active);
        }
    }

    public record GroupResponse(long id, String name, String description, CommunityGroup.Category category,
                                String meets, String location, Integer capacity, CommunityGroup.Visibility visibility,
                                CommunityGroup.JoinPolicy joinPolicy, boolean active, long members) {
        static GroupResponse of(CommunityGroup g, long count) {
            return new GroupResponse(g.getId(), g.getName(), g.getDescription(), g.getCategory(), g.getMeets(),
                    g.getLocation(), g.getCapacity(), g.getVisibility(), g.getJoinPolicy(), g.isActive(), count);
        }
    }

    public record AddMember(@Schema(example = "EMAIL") @NotNull OtpChannel channel,
                            @Schema(example = "lakshmi@example.org") @NotBlank @Size(max = 254) String contact) { }

    public record MemberResponse(long id, GroupMember.Status status, String name, OtpChannel channel, String contact,
                                 OffsetDateTime requestedAt, OffsetDateTime joinedAt) {
        static MemberResponse of(GroupService.MemberView v) {
            DevoteeAccount a = v.account();
            return new MemberResponse(v.member().getId(), v.member().getStatus(), a == null ? null : a.getFullName(),
                    a == null ? null : a.getChannel(), a == null ? null : a.getContact(),
                    v.member().getRequestedAt(), v.member().getJoinedAt());
        }
    }

    /** What anyone may see: no member names, a count only. */
    public record PublicGroup(long id, String name, String description, CommunityGroup.Category category, String meets,
                              String location, CommunityGroup.JoinPolicy joinPolicy, long members, boolean full) {
        static PublicGroup of(CommunityGroup g, long count) {
            return new PublicGroup(g.getId(), g.getName(), g.getDescription(), g.getCategory(), g.getMeets(),
                    g.getLocation(), g.getJoinPolicy(), count, g.getCapacity() != null && count >= g.getCapacity());
        }
    }

    public record MyGroup(long groupId, String name, CommunityGroup.Category category, String meets, String location,
                          GroupMember.Status status) {
        static MyGroup of(GroupService.Membership m) {
            return new MyGroup(m.group().getId(), m.group().getName(), m.group().getCategory(), m.group().getMeets(),
                    m.group().getLocation(), m.member().getStatus());
        }
    }
}
