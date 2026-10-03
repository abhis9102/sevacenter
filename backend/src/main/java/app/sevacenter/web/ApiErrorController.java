package app.sevacenter.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.autoconfigure.error.AbstractErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Errors are always JSON, whatever the client's Accept header. Replaces Boot's
 * BasicErrorController, which served browsers the "Whitelabel Error Page" (a well-known Spring
 * Boot fingerprint; found by DAST, G5).
 *
 * <p>Uses {@link ErrorAttributeOptions#defaults()} (status, error, path) unconditionally, so no
 * property can switch on messages, exception names or stack traces.
 */
@Hidden // internal error dispatch, not part of the API (and not a target for the spec-driven DAST)
@RestController
public class ApiErrorController extends AbstractErrorController {

    public ApiErrorController(ErrorAttributes errorAttributes) {
        super(errorAttributes);
    }

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        // Called directly rather than as an error dispatch: nothing to report. Without this it
        // answered 500 with status 999 (found by DAST, G5).
        if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", 404, "error", "Not Found"));
        }
        HttpStatus status = getStatus(request);
        if (status == HttpStatus.NO_CONTENT) {
            return new ResponseEntity<>(status);
        }
        Map<String, Object> body = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        // No timestamp: identical requests must get byte-identical errors. A per-response value
        // made every 401/400 differ, which DAST's boolean SQL-injection test read as injection
        // (POST /donations amount, GET /receipts paging; same lesson as G5).
        body.remove("timestamp");
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON) // preset: skips content negotiation
                .body(body);
    }
}
