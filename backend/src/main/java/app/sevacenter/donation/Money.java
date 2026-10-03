package app.sevacenter.donation;

import java.math.BigDecimal;

import app.sevacenter.web.InvalidFieldException;

/**
 * Rupee amounts on the wire are decimal strings ("1500", "1500.50"), converted exactly to paise:
 * never a double (0.1 + 0.2 != 0.3), never a JSON number a client library might round.
 */
final class Money {

    private static final String RUPEES = "[1-9][0-9]{0,8}(\\.[0-9]{1,2})?|0\\.[0-9]{1,2}";

    private Money() {
    }

    static long toPaise(String field, String rupees) {
        if (rupees == null || !rupees.matches(RUPEES)) {
            throw new InvalidFieldException(field, field + " must be rupees like 1500 or 1500.50 (max 99,99,99,999.99)");
        }
        long paise = new BigDecimal(rupees).movePointRight(2).longValueExact();
        if (paise <= 0) {
            throw new InvalidFieldException(field, field + " must be more than zero");
        }
        return paise;
    }

    static String toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }
}
