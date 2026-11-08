package com.clinicbook.auth;

import com.clinicbook.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new RateLimitProperties(new RateLimitProperties.Login(2, Duration.ofMinutes(1))),
            clock);

    @Test
    void allowsAttemptsUpToTheConfiguredMaximum() {
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void blocksAttemptsOverTheConfiguredMaximum() {
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void startsAFreshWindowAfterTheWindowElapses() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void tracksClientsIndependently() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void exposesWindowLengthInSeconds() {
        assertThat(limiter.windowSeconds()).isEqualTo(60);
    }

    @Test
    void resetClearsCounters() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        limiter.reset();

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
