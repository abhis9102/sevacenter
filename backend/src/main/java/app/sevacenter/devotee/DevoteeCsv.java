package app.sevacenter.devotee;

import java.util.List;

import org.apache.commons.csv.CSVFormat;

/**
 * The devotee CSV layout, shared by export and import so a file exported here can be imported
 * back. Cells are neutralised against CSV/formula injection on export (OWASP): a value starting
 * with {@code = + - @}, tab or CR would run as a formula in Excel or Sheets, so it gets a
 * leading {@code '}. Import strips that quote again.
 */
final class DevoteeCsv {

    /** Editable columns, in order; import requires exactly these (plus any read-only ones). */
    static final List<String> COLUMNS = List.of("fullName", "phone", "email", "addressLine", "city", "state",
            "pincode", "dateOfBirth", "consentSource");

    /** Export-only columns: ignored on import (import always creates new records). */
    static final List<String> READ_ONLY = List.of("id", "consentGivenAt", "createdAt");

    static final CSVFormat WRITE = CSVFormat.RFC4180.builder()
            .setHeader(concat(READ_ONLY.subList(0, 1), COLUMNS, READ_ONLY.subList(1, 3)).toArray(String[]::new))
            .get();

    static final CSVFormat READ = CSVFormat.RFC4180.builder()
            .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true)
            .setMaxRows(DevoteeImportService.MAX_ROWS + 1L)
            .get();

    private DevoteeCsv() {
    }

    static String neutralise(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        return !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
    }

    static String unneutralise(String cell) {
        return cell.length() > 1 && cell.charAt(0) == '\'' && "=+-@\t\r".indexOf(cell.charAt(1)) >= 0
                ? cell.substring(1) : cell;
    }

    @SafeVarargs
    private static List<String> concat(List<String>... parts) {
        return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
    }
}
