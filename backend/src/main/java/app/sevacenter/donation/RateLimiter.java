package app.sevacenter.donation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Fixed-window limit on public order creation per client IP (ADR 0013): every order is an API
 * call to the trust's gateway and a row here, so an anonymous endpoint must not be a free loop.
 * In-memory, per instance, like the login throttle (shared store in M6).
 */
@Component
public class RateLimiter {

    static final int ORDERS_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 50_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    /** True if this key may make one more call now (and counts it). */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() >= MAX_KEYS) {
            windows.values().removeIf(w -> !now.isBefore(w.end()));
        }
        Window w = windows.compute(key, (k, old) -> old == null || !now.isBefore(old.end())
                ? new Window(1, now.plus(WINDOW)) : new Window(old.count() + 1, old.end()));
        return w.count() <= ORDERS_PER_WINDOW;
    }

    private record Window(int count, Instant end) { }
}
