package com.skillmap.api.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLoginAttemptLimiterTest {

    private static final String IP = "127.0.0.1";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
    private final InMemoryLoginAttemptLimiter limiter = new InMemoryLoginAttemptLimiter(clock);

    @Test
    void blocksTheEmailForFifteenMinutesAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ana@test.local", IP);
        }
        assertThat(limiter.blockedFor("ana@test.local", IP)).isEmpty();

        limiter.recordFailure("ana@test.local", IP);
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(15));
        assertThat(limiter.blockedFor("otro@test.local", IP)).isEmpty();

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(5));
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.blockedFor("ana@test.local", IP)).isEmpty();
    }

    @Test
    void eachNewLockoutDoublesTheWaitUpTo24Hours() {
        lockOut("ana@test.local");
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(15));
        clock.advance(Duration.ofMinutes(15));

        lockOut("ana@test.local");
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(30));
        clock.advance(Duration.ofMinutes(30));

        lockOut("ana@test.local");
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(60));

        assertThat(InMemoryLoginAttemptLimiter.lockDuration(20)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void resetOnSuccessStartsTheEmailFromZero() {
        lockOut("ana@test.local");
        clock.advance(Duration.ofMinutes(15));
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ana@test.local", IP);
        }
        limiter.reset("ana@test.local");

        limiter.recordFailure("ana@test.local", IP);
        assertThat(limiter.blockedFor("ana@test.local", IP)).isEmpty();
        // El siguiente bloqueo vuelve a ser de 15 minutos: el acierto borró también los bloqueos anteriores.
        lockOut("ana@test.local");
        assertThat(limiter.blockedFor("ana@test.local", IP)).contains(Duration.ofMinutes(15));
    }

    @Test
    void oldFailuresAreForgotten() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("ana@test.local", IP);
        }
        clock.advance(Duration.ofMinutes(61));
        limiter.recordFailure("ana@test.local", IP);
        assertThat(limiter.blockedFor("ana@test.local", IP)).isEmpty();
    }

    @Test
    void theIpIsCappedAcrossManyEmailsAndASuccessDoesNotClearIt() {
        for (int i = 0; i < 19; i++) {
            limiter.recordFailure("user" + i + "@test.local", IP);
        }
        assertThat(limiter.blockedFor("nuevo@test.local", IP)).isEmpty();

        limiter.recordFailure("user19@test.local", IP);
        limiter.reset("user19@test.local");
        assertThat(limiter.blockedFor("nuevo@test.local", IP)).contains(Duration.ofMinutes(15));
        assertThat(limiter.blockedFor("nuevo@test.local", "10.0.0.2")).isEmpty();
    }

    private void lockOut(String email) {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(email, IP);
        }
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
