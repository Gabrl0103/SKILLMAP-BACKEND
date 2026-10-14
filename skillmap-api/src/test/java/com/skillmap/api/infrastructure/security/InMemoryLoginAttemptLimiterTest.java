package com.skillmap.api.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
    private final InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(clock);

    @Test
    void blocksAfterFiveFailuresForTheSameKeyOnly() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ana@test.local|127.0.0.1");
        }
        assertThat(limiter.isBlocked("ana@test.local|127.0.0.1")).isFalse();

        limiter.recordFailure("ana@test.local|127.0.0.1");
        assertThat(limiter.isBlocked("ana@test.local|127.0.0.1")).isTrue();
        assertThat(limiter.isBlocked("ana@test.local|10.0.0.2")).isFalse();
        assertThat(limiter.isBlocked("otro@test.local|127.0.0.1")).isFalse();
    }

    @Test
    void failuresOlderThanTenMinutesDoNotCount() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("k");
        }
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(limiter.isBlocked("k")).isFalse();

        limiter.recordFailure("k");
        assertThat(limiter.isBlocked("k")).isFalse();
    }

    @Test
    void resetClearsTheFailures() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("k");
        }
        limiter.reset("k");
        assertThat(limiter.isBlocked("k")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
