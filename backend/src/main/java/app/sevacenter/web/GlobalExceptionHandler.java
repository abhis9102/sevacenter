package app.sevacenter.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import app.sevacenter.auth.SlugAlreadyTakenException;
import app.sevacenter.devotee.DevoteeImportService;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.donation.DonationService;
import app.sevacenter.user.UserManagementService;

/**
 * Turns exceptions into clean API errors. Deliberately terse: it reports what the caller
 * got wrong (validation, slug taken) and never leaks stack traces or internal detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserManagementService.UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onUserNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(UserManagementService.AvatarNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onAvatarNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "avatar_not_found"));
    }

    @ExceptionHandler(DevoteeService.DevoteeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onDevoteeNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(DonationService.DonationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onDonationNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(app.sevacenter.donation.ReceiptService.ReceiptNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onReceiptNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(DonationService.LedgerConflictException.class)
    public ResponseEntity<Map<String, Object>> onLedgerConflict(DonationService.LedgerConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DevoteeImportService.ImportRejectedException.class)
    public ResponseEntity<Map<String, Object>> onImportRejected(DevoteeImportService.ImportRejectedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("rows", ex.errors());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<Map<String, Object>> onInvalidField(InvalidFieldException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("fields", Map.of(ex.field(), ex.getMessage()));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(UserManagementService.UserConflictException.class)
    public ResponseEntity<Map<String, Object>> onUserConflict(UserManagementService.UserConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UserManagementService.InvalidSetupTokenException.class)
    public ResponseEntity<Map<String, Object>> onInvalidSetupToken() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_or_expired_link"));
    }

    @ExceptionHandler(app.sevacenter.user.PasswordResetService.InvalidResetTokenException.class)
    public ResponseEntity<Map<String, Object>> onInvalidResetToken() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_or_expired_link"));
    }

    // Found by DAST (M2 slice 2): these surfaced as 500s.

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> onUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of("error", "upload_too_large"));
    }

    /** A malformed multipart body (e.g. a bad Content-Disposition on a part). */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> onBadUpload() {
        return ResponseEntity.badRequest().body(Map.of("error", "malformed_upload"));
    }

    /** The row changed or was deleted by a concurrent request between our read and write. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> onConcurrentChange() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "concurrent_modification"));
    }

    @ExceptionHandler(app.sevacenter.auth.LoginThrottle.TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> onTooManyAttempts() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "too_many_attempts"));
    }

    @ExceptionHandler(app.sevacenter.donation.OnlineDonationService.PaymentNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> onPaymentNotVerified() {
        return ResponseEntity.badRequest().body(Map.of("error", "payment_not_verified"));
    }

    @ExceptionHandler(app.sevacenter.donation.OnlineDonationService.PaymentsNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> onPaymentsNotConfigured() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "payments_not_configured"));
    }

    @ExceptionHandler(app.sevacenter.donation.OnlineDonationService.GatewayUnavailableException.class)
    public ResponseEntity<Map<String, Object>> onGatewayUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "gateway_unavailable"));
    }

    @ExceptionHandler(app.sevacenter.portal.DevoteeLoginService.InvalidCodeException.class)
    public ResponseEntity<Map<String, Object>> onInvalidCode() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_code"));
    }

    @ExceptionHandler(app.sevacenter.portal.DevoteeLoginService.ChannelUnavailableException.class)
    public ResponseEntity<Map<String, Object>> onChannelUnavailable() {
        return ResponseEntity.badRequest().body(Map.of("error", "channel_unavailable"));
    }

    @ExceptionHandler(app.sevacenter.portal.DevoteeLoginService.DeliveryFailedException.class)
    public ResponseEntity<Map<String, Object>> onDeliveryFailed() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "delivery_failed"));
    }

    @ExceptionHandler(app.sevacenter.portal.PortalController.NotLoggedInException.class)
    public ResponseEntity<Map<String, Object>> onDevoteeNotLoggedIn() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "not_logged_in"));
    }

    @ExceptionHandler({app.sevacenter.event.EventService.EventNotFoundException.class,
            app.sevacenter.event.EventService.PassNotFoundException.class,
            app.sevacenter.sevak.SevakService.SignupNotFoundException.class,
            app.sevacenter.puja.PujaService.PujaNotFoundException.class,
            app.sevacenter.portal.DevoteeLoginService.NotOnATrustHostException.class})
    public ResponseEntity<Map<String, Object>> onEventOrPassNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(app.sevacenter.puja.PujaService.PujaConflictException.class)
    public ResponseEntity<Map<String, Object>> onPujaConflict(app.sevacenter.puja.PujaService.PujaConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(app.sevacenter.event.EventService.EventConflictException.class)
    public ResponseEntity<Map<String, Object>> onEventConflict(app.sevacenter.event.EventService.EventConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(app.sevacenter.event.EventService.AlreadyCheckedInException.class)
    public ResponseEntity<Map<String, Object>> onAlreadyCheckedIn(app.sevacenter.event.EventService.AlreadyCheckedInException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "already_checked_in");
        body.put("checkedInAt", String.valueOf(ex.pass().getCheckedInAt()));
        body.put("attendeeName", ex.pass().getAttendeeName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException ex) {
        // Sorted, so identical requests get byte-identical responses. With a HashMap the field
        // order varied between identical requests, which DAST read as SQL injection (G5).
        Map<String, String> fields = new TreeMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(SlugAlreadyTakenException.class)
    public ResponseEntity<Map<String, Object>> onSlugTaken(SlugAlreadyTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "slug_taken",
                        "message", "That subdomain is already registered."));
    }
}
