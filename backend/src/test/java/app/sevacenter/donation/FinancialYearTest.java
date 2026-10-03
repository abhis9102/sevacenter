package app.sevacenter.donation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import app.sevacenter.donation.DonationService.FinancialYear;
import org.junit.jupiter.api.Test;

/** The Indian financial year: 1 April to 31 March (decides which FY "today" belongs to). */
class FinancialYearTest {

    @Test
    void theYearTurnsOnTheFirstOfApril() {
        assertThat(FinancialYear.containing(LocalDate.of(2026, 3, 31))).isEqualTo(new FinancialYear(2025));
        assertThat(FinancialYear.containing(LocalDate.of(2026, 4, 1))).isEqualTo(new FinancialYear(2026));
        assertThat(FinancialYear.containing(LocalDate.of(2026, 1, 15))).isEqualTo(new FinancialYear(2025));
        assertThat(FinancialYear.containing(LocalDate.of(2026, 12, 31))).isEqualTo(new FinancialYear(2026));
    }

    @Test
    void boundsAndLabel() {
        FinancialYear fy = new FinancialYear(2026);
        assertThat(fy.start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(fy.label()).isEqualTo("2026-27");
        assertThat(new FinancialYear(2099).label()).isEqualTo("2099-00");
    }
}
