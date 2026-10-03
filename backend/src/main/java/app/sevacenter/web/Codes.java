package app.sevacenter.web;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Short random codes people read aloud and type at a counter or gate (event passes, puja bookings):
 * 10 symbols from an alphabet without 0/O/1/I, SecureRandom, i.e. 50 bits. Bearer values.
 */
public final class Codes {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {
    }

    /** A fresh code that {@code taken} doesn't already know. */
    public static String fresh(Predicate<String> taken) {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (!taken.test(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("could not allocate a code");
    }

    /** Tolerates case, spaces and dashes; null if it can't be a code. */
    public static String normalise(String raw) {
        String c = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return c.matches("[A-Z2-9]{10}") ? c : null;
    }
}
