package app.sevacenter.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Basic brute-force protection (ADR 0009): after {@value #MAX_FAILURES} failed logins for an
 * account, or {@value #MAX_IP_FAILURES} from one client IP, within the window, that key is locked
 * until the window ends. Keys are counted whether or not the account exists, so lockouts don't
 * reveal which emails are real.
 *
 * <p>The IP limit is much higher because an IP is often shared: a mobile carrier's NAT puts many
 * devotees and staff behind one address, and a low limit would let anyone on it lock everyone
 * out. It exists to catch one address spraying many accounts. The client IP is the real one even
 * behind the load balancer (forwarded headers from trusted proxies only; application.yml).
 *
 * <p>In-memory, so per instance; it moves to a shared store before running more than one task
 * (M6). Full rate limiting arrives in M4.
 */
@Component
public class LoginThrottle {

    public static final int MAX_FAILURES = 5;
    public static final int MAX_IP_FAILURES = 50;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 50_000;
    private static final String IP_PREFIX = "ip:";

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
        int limit = key.startsWith(IP_PREFIX) ? MAX_IP_FAILURES : MAX_FAILURES;
        return f != null && !f.expired(now()) && f.count() >= limit;
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

    public static String accountKey(long tenantId, String email) {
        return "acct:" + tenantId + ":" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    static String ipKey(String ip) {
        return IP_PREFIX + ip;
    }

    private Instant now() {
        return clock.instant();
    }

    /** 429: a locked key (see LoginThrottleFilter for login; also used by password change). */
    public static class TooManyAttemptsException extends RuntimeException { }

    private record Failures(int count, Instant windowEnd) {
        boolean expired(Instant now) {
            return !now.isBefore(windowEnd);
        }
    }
}
