package com.clinicbook.auth;

import com.clinicbook.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter keyed by client identifier. A single instance is
 * enough for the current deployment; a shared store is required once the API scales out.
 */
@Component
public class LoginRateLimiter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(RateLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.login().maxAttempts();
        this.window = properties.login().window();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > PRUNE_THRESHOLD) {
            prune(now);
        }
        WindowState state = windows.computeIfAbsent(key, ignored -> new WindowState(now));
        synchronized (state) {
            if (!now.isBefore(state.start.plus(window))) {
                state.start = now;
                state.count = 0;
            }
            if (state.count >= maxAttempts) {
                return false;
            }
            state.count++;
            return true;
        }
    }

    public long windowSeconds() {
        return Math.max(1, window.toSeconds());
    }

    public void reset() {
        windows.clear();
    }

    private void prune(Instant now) {
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().start.plus(window)));
    }

    private static final class WindowState {

        private Instant start;
        private int count;

        private WindowState(Instant start) {
            this.start = start;
        }
    }
}
