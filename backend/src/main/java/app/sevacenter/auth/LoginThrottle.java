package app.sevacenter.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Basic brute-force protection (ADR 0009): after {@value #MAX_FAILURES} failed logins within
 * the window, a key (an account, or a client IP) is locked until the window ends. Keys are
 * counted whether or not the account exists, so lockouts don't reveal which emails are real.
 *
 * <p>In-memory, so per instance; it moves to a shared store before running more than one task
 * (M6). Full rate limiting arrives in M4.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String key) {
        Failures f = failures.get(key);
        return f != null && !f.expired(now()) && f.count() >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        Instant now = now();
        if (failures.size() >= MAX_TRACKED_KEYS) {
            failures.values().removeIf(f -> f.expired(now)); // bound memory under a spray attack
        }
        failures.compute(key, (k, f) -> f == null || f.expired(now)
                ? new Failures(1, now.plus(WINDOW)) : new Failures(f.count() + 1, f.windowEnd()));
    }

    public void reset(String key) {
        failures.remove(key);
    }

    static String accountKey(long tenantId, String email) {
        return "acct:" + tenantId + ":" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private Instant now() {
        return clock.instant();
    }

    private record Failures(int count, Instant windowEnd) {
        boolean expired(Instant now) {
            return !now.isBefore(windowEnd);
        }
    }
}
