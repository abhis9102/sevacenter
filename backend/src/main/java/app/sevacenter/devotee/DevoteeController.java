package app.sevacenter.devotee;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.user.Role;
import app.sevacenter.web.InvalidFieldException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Devotee records (ADR 0010). MEMBER: view/search, masked, by name only. LEADER: also create
 * and edit, sees everything. TRUST_ADMIN: also erase. Masking happens here, on the server, from
 * the role re-read on every request: full values never reach a MEMBER's browser.
 */
@RestController
@RequestMapping("/api/v1/devotees")
public class DevoteeController {

    private static final LocalDate EARLIEST_BIRTH = LocalDate.of(1900, 1, 2);

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    private final DevoteeService service;
    private final DevoteeImportService importer;

    public DevoteeController(DevoteeService service, DevoteeImportService importer) {
        this.service = service;
        this.importer = importer;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public PageResponse search(@RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size,
                               @AuthenticationPrincipal StaffUser staff) {
        boolean full = seesContactDetails(staff);
        Page<Devotee> result = service.search(q, page, size, full);
        return new PageResponse(result.map(d -> DevoteeResponse.of(d, full)).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * The whole directory as CSV: TRUST_ADMIN only (bulk PII export is the highest-impact
     * exfiltration path, ADR 0010), never cached, audit-logged, cells neutralised against
     * formula injection.
     */
    @GetMapping(value = "/export", produces = {"text/csv", MediaType.ALL_VALUE})
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal StaffUser staff) throws IOException {
        List<Devotee> all = service.exportAll();
        StringBuilder out = new StringBuilder();
        try (CSVPrinter csv = new CSVPrinter(out, DevoteeCsv.WRITE)) {
            for (Devotee d : all) {
                csv.printRecord(Stream.of(d.getId(), d.getFullName(), d.getPhone(), d.getEmail(), d.getAddressLine(),
                        d.getCity(), d.getState(), d.getPincode(), d.getDateOfBirth(), d.getConsentSource(),
                        d.getConsentGivenAt(), d.getCreatedAt()).map(DevoteeCsv::neutralise).toList());
            }
        }
        AUDIT.info("event=devotee_export tenant={} user={} rows={}", staff.tenantId(), staff.userId(), all.size());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("devotees-" + LocalDate.now() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** All-or-nothing CSV import (same rules as the API); TRUST_ADMIN only, like export. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public ImportResponse importCsv(@RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal StaffUser staff) throws IOException {
        try (var in = file.getInputStream()) {
            return new ImportResponse(importer.importCsv(in, staff.userId(), staff.tenantId()));
        }
    }

    public record ImportResponse(int imported) { }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('MEMBER')")
    public DevoteeResponse get(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        return DevoteeResponse.of(service.get(id), seesContactDetails(staff));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public DevoteeResponse create(@Valid @RequestBody DevoteeDetails request,
                                  @AuthenticationPrincipal StaffUser staff) {
        if (request.consentSource() == null) {
            throw new InvalidFieldException("consentSource", "consent is required to record a devotee");
        }
        return DevoteeResponse.of(service.create(request.toDomain(), request.consentSource(), staff.userId()), true);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('LEADER')")
    public DevoteeResponse update(@PathVariable long id, @Valid @RequestBody DevoteeDetails request,
                                  @AuthenticationPrincipal StaffUser staff) {
        return DevoteeResponse.of(service.update(id, request.toDomain(), staff.userId()), true);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    public void erase(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        service.erase(id, staff.userId());
    }

    private static boolean seesContactDetails(StaffUser staff) {
        return staff.role() != Role.MEMBER;
    }

    /**
     * The editable fields (plus consent, on create only); limits mirror the V5 checks, so bad input is a 400, never a database
     * error. Examples are valid on purpose, so DAST attacks reach the database.
     */
    public record DevoteeDetails(
            @Schema(example = "Lakshmi Iyer") @NotBlank @Size(max = 120) String fullName,
            @Schema(example = "98765 43210") @Size(max = 30) String phone,
            @Schema(example = "lakshmi@example.org") @Email @Size(max = 254) String email,
            @Schema(example = "12 Temple Street") @Size(max = 200) String addressLine,
            @Schema(example = "Pune") @Size(max = 80) String city,
            @Schema(example = "Maharashtra") @Size(max = 80) String state,
            @Schema(example = "411001") @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be 6 digits")
            String pincode,
            @Schema(example = "1980-05-14") @Past LocalDate dateOfBirth,
            @Schema(example = "IN_PERSON", description = "Required on create; set once, ignored on update")
            ConsentSource consentSource) {

        Devotee.Details toDomain() {
            if (dateOfBirth != null && dateOfBirth.isBefore(EARLIEST_BIRTH)) {
                throw new InvalidFieldException("dateOfBirth", "dateOfBirth is too far in the past");
            }
            return new Devotee.Details(fullName, phone, email, addressLine, city, state, pincode, dateOfBirth);
        }
    }

    public record DevoteeResponse(long id, String fullName, String phone, String email, String addressLine,
                                  String city, String state, String pincode, LocalDate dateOfBirth,
                                  ConsentSource consentSource, OffsetDateTime consentGivenAt,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean masked) {

        static DevoteeResponse of(Devotee d, boolean full) {
            if (full) {
                return new DevoteeResponse(d.getId(), d.getFullName(), d.getPhone(), d.getEmail(), d.getAddressLine(),
                        d.getCity(), d.getState(), d.getPincode(), d.getDateOfBirth(), d.getConsentSource(),
                        d.getConsentGivenAt(), d.getCreatedAt(), d.getUpdatedAt(), false);
            }
            return new DevoteeResponse(d.getId(), d.getFullName(), maskPhone(d.getPhone()), maskEmail(d.getEmail()),
                    null, d.getCity(), d.getState(), null, null, d.getConsentSource(), d.getConsentGivenAt(),
                    d.getCreatedAt(), d.getUpdatedAt(), true);
        }
    }

    public record PageResponse(List<DevoteeResponse> items, int page, int size, long total) { }

    /** "+919876543210" -> "+91******3210". */
    static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        int keep = 4;
        int prefix = phone.startsWith("+91") ? 3 : 1;
        return phone.substring(0, prefix) + "*".repeat(Math.max(0, phone.length() - prefix - keep))
                + phone.substring(phone.length() - keep);
    }

    /** "lakshmi@example.org" -> "l***@example.org". */
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
