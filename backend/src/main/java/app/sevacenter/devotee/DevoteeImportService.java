package app.sevacenter.devotee;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.sevacenter.devotee.DevoteeController.DevoteeDetails;
import app.sevacenter.web.InvalidFieldException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk import of devotees from CSV (M2, ADR 0010). All-or-nothing: every row is validated with
 * the same rules as the API first, and nothing is written unless all rows pass. Errors name the
 * row and field but never echo cell values, so an import report can't reflect content back.
 */
@Service
public class DevoteeImportService {

    static final int MAX_ROWS = 5_000;
    static final int MAX_ERRORS = 100;
    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final DevoteeService devotees;
    private final Validator validator;

    public DevoteeImportService(DevoteeService devotees, Validator validator) {
        this.devotees = devotees;
        this.validator = validator;
    }

    @Transactional
    public int importCsv(InputStream csv, long staffId, long tenantId) {
        List<Row> rows = parse(csv);
        for (Row row : rows) {
            devotees.create(row.details(), row.consent(), staffId);
        }
        audit.info("event=devotee_import tenant={} user={} rows={}", tenantId, staffId, rows.size());
        return rows.size();
    }

    private List<Row> parse(InputStream csv) {
        List<Row> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        // Excel writes a UTF-8 BOM; without stripping it the first header would be "﻿id".
        try (Reader reader = new InputStreamReader(BOMInputStream.builder().setInputStream(csv).get(), StandardCharsets.UTF_8);
             CSVParser parser = DevoteeCsv.READ.parse(reader)) {
            checkHeader(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                if (record.getRecordNumber() > MAX_ROWS) {
                    throw new ImportRejectedException("too_many_rows", List.of());
                }
                int line = (int) record.getRecordNumber() + 1; // +1: the header is line 1
                if (isBlank(record)) {
                    continue;
                }
                try {
                    Row row = row(record, line, errors);
                    if (row != null) {
                        rows.add(row);
                    }
                } catch (InvalidFieldException e) {
                    errors.add(new RowError(line, e.field(), e.getMessage()));
                }
                if (errors.size() >= MAX_ERRORS) {
                    break;
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            // Malformed CSV (unbalanced quotes, a short row, bad encoding). No parser detail in the
            // response: it can contain cell content.
            throw new ImportRejectedException("malformed_csv", List.of());
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            throw new ImportRejectedException("empty", List.of());
        }
        if (!errors.isEmpty()) {
            throw new ImportRejectedException("invalid_rows", errors);
        }
        return rows;
    }

    private static void checkHeader(List<String> header) {
        Set<String> seen = new HashSet<>(header);
        boolean unknown = header.stream().anyMatch(h -> !DevoteeCsv.COLUMNS.contains(h) && !DevoteeCsv.READ_ONLY.contains(h));
        if (unknown || !seen.containsAll(DevoteeCsv.COLUMNS) || seen.size() != header.size()) {
            throw new ImportRejectedException("bad_header", List.of());
        }
    }

    private Row row(CSVRecord r, int line, List<RowError> errors) {
        LocalDate dob = null;
        String rawDob = cell(r, "dateOfBirth");
        if (rawDob != null) {
            try {
                dob = LocalDate.parse(rawDob);
            } catch (DateTimeParseException e) {
                throw new InvalidFieldException("dateOfBirth", "dateOfBirth must be YYYY-MM-DD");
            }
        }
        ConsentSource consent = null;
        String rawConsent = cell(r, "consentSource");
        if (rawConsent != null) {
            try {
                consent = ConsentSource.valueOf(rawConsent.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidFieldException("consentSource", "consentSource must be one of IN_PERSON, PHONE, ONLINE_FORM, WRITTEN");
            }
        }
        DevoteeDetails details = new DevoteeDetails(cell(r, "fullName"), cell(r, "phone"), cell(r, "email"),
                cell(r, "addressLine"), cell(r, "city"), cell(r, "state"), cell(r, "pincode"), dob, consent);
        int before = errors.size();
        for (ConstraintViolation<DevoteeDetails> v : validator.validate(details)) {
            errors.add(new RowError(line, v.getPropertyPath().toString(), v.getMessage()));
        }
        if (consent == null) {
            errors.add(new RowError(line, "consentSource", "consent is required to record a devotee"));
        }
        if (errors.size() > before) {
            return null;
        }
        // Same normalisation as the API (phone, NUL): throws InvalidFieldException -> a row error.
        return new Row(DevoteeService.normalise(details.toDomain()), consent);
    }

    private static boolean isBlank(CSVRecord r) {
        for (String col : DevoteeCsv.COLUMNS) {
            String val = r.isMapped(col) ? r.get(col) : null;
            if (val != null && !val.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(CSVRecord r, String column) {
        String value = r.get(column);
        return value == null || value.isBlank() ? null : DevoteeCsv.unneutralise(value);
    }

    record Row(Devotee.Details details, ConsentSource consent) { }

    public record RowError(int line, String field, String message) { }

    /** 400 with a reason code and (for invalid rows) the per-row errors. */
    public static class ImportRejectedException extends RuntimeException {
        private final List<RowError> errors;

        public ImportRejectedException(String reason, List<RowError> errors) {
            super(reason);
            this.errors = List.copyOf(errors);
        }

        public List<RowError> errors() {
            return errors;
        }
    }
}
