package com.dnd.moyeolak.global.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryRateLimitCounter implements RateLimitCounter {

    private final ConcurrentMap<String, CounterState> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitCounter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increaseAndGet(String key, Instant expiresAt) {
        Instant now = clock.instant();
        CounterState state = counters.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new CounterState(new AtomicLong(1), expiresAt);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return state.count().get();
    }

    private record CounterState(AtomicLong count, Instant expiresAt) {
    }
}
