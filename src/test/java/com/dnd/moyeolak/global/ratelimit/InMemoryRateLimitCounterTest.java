package com.dnd.moyeolak.global.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitCounterTest {

    @Test
    @DisplayName("같은 윈도우의 key 카운트를 증가시킨다")
    void increaseAndGet_incrementsSameKeyCount() {
        InMemoryRateLimitCounter counter = new InMemoryRateLimitCounter(fixedClock("2026-07-22T00:00:00Z"));
        Instant expiresAt = Instant.parse("2026-07-23T00:00:00Z");

        long first = counter.increaseAndGet("midpoint:meeting:daily:20260722:meeting-1", expiresAt);
        long second = counter.increaseAndGet("midpoint:meeting:daily:20260722:meeting-1", expiresAt);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
    }

    @Test
    @DisplayName("만료된 key는 새 카운터로 다시 시작한다")
    void increaseAndGet_resetsExpiredKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        InMemoryRateLimitCounter counter = new InMemoryRateLimitCounter(clock);

        counter.increaseAndGet("midpoint:ip:minute:202607220900:203.0.113.10",
                Instant.parse("2026-07-22T00:01:00Z"));
        clock.advance(Duration.ofMinutes(2));

        long count = counter.increaseAndGet("midpoint:ip:minute:202607220900:203.0.113.10",
                Instant.parse("2026-07-22T00:03:00Z"));

        assertThat(count).isEqualTo(1);
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
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
