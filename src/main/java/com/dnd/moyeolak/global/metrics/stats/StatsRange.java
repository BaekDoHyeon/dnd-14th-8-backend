package com.dnd.moyeolak.global.metrics.stats;

import java.time.Duration;

/**
 * 추이 조회 범위와 버킷 크기. 24h→1h, 7d→6h, 30d→1d.
 */
public enum StatsRange {

    H24("24h", Duration.ofHours(24), Duration.ofHours(1)),
    D7("7d", Duration.ofDays(7), Duration.ofHours(6)),
    D30("30d", Duration.ofDays(30), Duration.ofDays(1));

    private final String code;
    private final Duration window;
    private final Duration bucket;

    StatsRange(String code, Duration window, Duration bucket) {
        this.code = code;
        this.window = window;
        this.bucket = bucket;
    }

    public String code() {
        return code;
    }

    public Duration window() {
        return window;
    }

    public Duration bucket() {
        return bucket;
    }

    /** 알 수 없는 값은 기본 24h로 처리한다. */
    public static StatsRange from(String code) {
        for (StatsRange range : values()) {
            if (range.code.equalsIgnoreCase(code)) {
                return range;
            }
        }
        return H24;
    }
}
