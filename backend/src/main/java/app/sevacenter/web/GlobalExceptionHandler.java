package app.sevacenter.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.sevacenter.auth.SlugAlreadyTakenException;
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

    @ExceptionHandler(UserManagementService.UserConflictException.class)
    public ResponseEntity<Map<String, Object>> onUserConflict(UserManagementService.UserConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UserManagementService.InvalidSetupTokenException.class)
    public ResponseEntity<Map<String, Object>> onInvalidSetupToken() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_or_expired_link"));
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
