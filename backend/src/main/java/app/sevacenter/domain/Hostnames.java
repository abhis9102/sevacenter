package app.sevacenter.domain;

import java.net.IDN;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import app.sevacenter.web.InvalidFieldException;

/** What a trust may connect as its own domain (ADR 0036): a public name that isn't ours. */
final class Hostnames {

    private static final Pattern NAME =
            Pattern.compile("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    /** Never public, or documentation-only (RFC 2606/6761/8375). */
    private static final Set<String> RESERVED_TLDS =
            Set.of("localhost", "local", "internal", "test", "example", "invalid", "lan", "home", "arpa", "onion");

    private Hostnames() { }

    /** Lowercase ASCII (punycode for IDNs), no trailing dot; or InvalidFieldException. */
    static String normalise(String raw, List<String> ourBaseDomains) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Enter the domain, e.g. www.yourtemple.org");
        }
        String h = raw.strip().toLowerCase(Locale.ROOT);
        if (h.startsWith("http://") || h.startsWith("https://") || h.contains("/") || h.contains(":") || h.contains("@")) {
            throw invalid("Enter only the domain, e.g. www.yourtemple.org (no https:// or paths)");
        }
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        try {
            h = IDN.toASCII(h, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalid("That isn't a valid domain name");
        }
        if (h.length() > 253 || !NAME.matcher(h).matches()) {
            throw invalid("That isn't a valid domain name");
        }
        String tld = h.substring(h.lastIndexOf('.') + 1);
        if (RESERVED_TLDS.contains(tld)) {
            throw invalid("Use a public domain you own");
        }
        for (String base : ourBaseDomains) {
            String b = base.toLowerCase(Locale.ROOT);
            if (h.equals(b) || h.endsWith("." + b)) {
                throw invalid("That's one of our own addresses; enter your temple's domain");
            }
        }
        return h;
    }

    private static InvalidFieldException invalid(String message) {
        return new InvalidFieldException("hostname", message);
    }
}
