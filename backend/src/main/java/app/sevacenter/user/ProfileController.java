package app.sevacenter.user;

import java.io.IOException;
import java.time.OffsetDateTime;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.web.InvalidFieldException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service staff profile dashboard: view details, update display name, notification
 * preferences, avatar, and change password.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final UserManagementService service;
    private final TenantRepository tenants;

    public ProfileController(UserManagementService service, TenantRepository tenants) {
        this.service = service;
        this.tenants = tenants;
    }

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal StaffUser user) {
        AppUser u = service.getProfile(user.userId());
        return ProfileResponse.of(u, tenants);
    }

    @PatchMapping
    public ProfileResponse updateProfile(@AuthenticationPrincipal StaffUser user,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        AppUser current = service.getProfile(user.userId());
        String displayName = request.displayName() != null ? request.displayName() : current.getDisplayName();
        boolean notifyDevotees = request.notifyDevotees() != null ? request.notifyDevotees() : current.isNotifyDevotees();
        boolean notifyDonations = request.notifyDonations() != null ? request.notifyDonations() : current.isNotifyDonations();
        boolean notifySecurity = request.notifySecurity() != null ? request.notifySecurity() : current.isNotifySecurity();

        AppUser updated = service.updateProfile(user.userId(), displayName, notifyDevotees, notifyDonations,
                notifySecurity);
        return ProfileResponse.of(updated, tenants);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal StaffUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(user.userId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal StaffUser user,
                                        @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidFieldException("avatar", "Avatar file is empty");
        }
        service.updateAvatar(user.userId(), file.getBytes());
        return ProfileResponse.of(service.getProfile(user.userId()), tenants);
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar(@AuthenticationPrincipal StaffUser user) {
        UserManagementService.AvatarRecord record = service.getAvatar(user.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .body(record.data());
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@AuthenticationPrincipal StaffUser user) {
        service.removeAvatar(user.userId());
    }

    public record ProfileResponse(
            long userId,
            String email,
            String displayName,
            String role,
            String tenant,
            String status,
            OffsetDateTime createdAt,
            boolean notifyDevotees,
            boolean notifyDonations,
            boolean notifySecurity,
            boolean hasAvatar
    ) {
        public static ProfileResponse of(AppUser u, TenantRepository tenants) {
            String slug = tenants.findById(u.getTenantId()).map(Tenant::getSlug).orElse(null);
            return new ProfileResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getDisplayName(),
                    u.getRole().name(),
                    slug,
                    u.getStatus().name(),
                    u.getCreatedAt(),
                    u.isNotifyDevotees(),
                    u.isNotifyDonations(),
                    u.isNotifySecurity(),
                    u.hasAvatar()
            );
        }
    }

    public record UpdateProfileRequest(
            @Schema(example = "Ravi Kumar") @Size(max = 120) String displayName,
            Boolean notifyDevotees,
            Boolean notifyDonations,
            Boolean notifySecurity
    ) {}

    public record ChangePasswordRequest(
            @Schema(example = "current-password-123") @NotBlank String currentPassword,
            @Schema(example = "new-secure-password-456")
            @NotBlank @Size(min = 12, max = 200, message = "password must be at least 12 characters")
            String newPassword
    ) {}
}
