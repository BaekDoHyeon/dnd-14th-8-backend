package com.dnd.moyeolak.global.ratelimit;

import java.time.Instant;

public interface RateLimitCounter {

    long increaseAndGet(String key, Instant expiresAt);
}
