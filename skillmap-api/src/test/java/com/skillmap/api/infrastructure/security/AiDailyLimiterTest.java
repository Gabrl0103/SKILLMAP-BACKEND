package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.exception.AiDailyLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiDailyLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
    private final AiDailyLimiter limiter = new AiDailyLimiter(20, 150, clock);

    @Test
    void allowsTwentyPerIpAndRejectsTheTwentyFirst() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire("1.1.1.1");
        }

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1"))
                .isInstanceOf(AiDailyLimitException.class)
                .hasMessageContaining("20");
    }

    @Test
    void eachIpHasItsOwnQuota() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire("1.1.1.1");
        }

        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void globalLimitAppliesAcrossIps() {
        AiDailyLimiter small = new AiDailyLimiter(20, 3, clock);
        small.acquire("1.1.1.1");
        small.acquire("2.2.2.2");
        small.acquire("3.3.3.3");

        assertThatThrownBy(() -> small.acquire("4.4.4.4"))
                .isInstanceOf(AiDailyLimitException.class)
                .hasMessageContaining("límite diario");
    }

    @Test
    void resetsOnTheNextDay() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire("1.1.1.1");
        }

        clock.advance(Duration.ofDays(1));

        assertThatCode(() -> limiter.acquire("1.1.1.1")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
            return now;
        }
    }
}
