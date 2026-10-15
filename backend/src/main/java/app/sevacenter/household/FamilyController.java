package app.sevacenter.household;

import java.time.LocalDate;

import app.sevacenter.portal.DevoteeAccount;
import app.sevacenter.portal.DevoteeLoginService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.portal.PortalController;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** My Mandir → My family (ADR 0039): the signed-in devotee manages their own household. */
@RestController
public class FamilyController {

    private final FamilyService family;
    private final DevoteeLoginService login;

    public FamilyController(FamilyService family, DevoteeLoginService login) {
        this.family = family;
        this.login = login;
    }

    @GetMapping("/api/v1/portal/family")
    public ResponseEntity<FamilyService.View> view(@CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.view(me(t)));
    }

    @PostMapping("/api/v1/portal/family")
    public ResponseEntity<FamilyService.View> start(@Valid @RequestBody StartRequest r,
                                                    @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.start(me(t), r.yourName(), r.familyName(), r.gotra()));
    }

    @PostMapping("/api/v1/portal/family/children")
    public ResponseEntity<FamilyService.View> addChild(@Valid @RequestBody ChildRequest r,
                                                       @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.addChild(me(t), r.fullName(), r.dateOfBirth(),
                r.relation() == null ? HouseholdMember.Relation.CHILD : r.relation()));
    }

    @PutMapping("/api/v1/portal/family/members/{memberId:\\d+}")
    public ResponseEntity<FamilyService.View> editChild(@PathVariable long memberId, @Valid @RequestBody ChildRequest r,
                                                        @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.editChild(me(t), memberId, r.fullName(), r.dateOfBirth()));
    }

    @DeleteMapping("/api/v1/portal/family/members/{memberId:\\d+}")
    public ResponseEntity<FamilyService.View> remove(@PathVariable long memberId,
                                                     @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.remove(me(t), memberId));
    }

    @PostMapping("/api/v1/portal/family/invites")
    public ResponseEntity<FamilyService.View> invite(@Valid @RequestBody InviteRequest r,
                                                     @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.invite(me(t), r.channel(), r.contact(), r.relation()));
    }

    @DeleteMapping("/api/v1/portal/family/invites/{id:\\d+}")
    public ResponseEntity<FamilyService.View> cancelInvite(@PathVariable long id,
                                                           @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.cancelInvite(me(t), id));
    }

    @PostMapping("/api/v1/portal/family/invitations/{id:\\d+}/accept")
    public ResponseEntity<FamilyService.View> accept(@PathVariable long id, @Valid @RequestBody(required = false) AcceptRequest r,
                                                     @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.accept(me(t), id, r == null ? null : r.yourName()));
    }

    @PostMapping("/api/v1/portal/family/invitations/{id:\\d+}/decline")
    public ResponseEntity<FamilyService.View> decline(@PathVariable long id,
                                                      @CookieValue(name = PortalController.COOKIE, required = false) String t) {
        return ok(family.decline(me(t), id));
    }

    private DevoteeAccount me(String token) {
        return login.resolve(token).orElseThrow(PortalController.NotLoggedInException::new);
    }

    private static ResponseEntity<FamilyService.View> ok(FamilyService.View v) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(v);
    }

    public record StartRequest(@Schema(example = "Ramesh Kulkarni") @Size(max = 120) String yourName,
                               @Schema(example = "Kulkarni family") @NotBlank @Size(max = 120) String familyName,
                               @Schema(example = "Kashyap") @Size(max = 60) String gotra) { }

    public record ChildRequest(@Schema(example = "Aarav Kulkarni") @NotBlank @Size(max = 120) String fullName,
                               @Schema(example = "2015-06-01") @Past LocalDate dateOfBirth,
                               @Schema(example = "CHILD") HouseholdMember.Relation relation) { }

    public record InviteRequest(@Schema(example = "EMAIL") @NotNull OtpChannel channel,
                                @Schema(example = "sunita@example.org") @NotBlank @Size(max = 254) String contact,
                                @Schema(example = "SPOUSE") @NotNull HouseholdMember.Relation relation) { }

    public record AcceptRequest(@Schema(example = "Sunita Kulkarni") @Size(max = 120) String yourName) { }
}
