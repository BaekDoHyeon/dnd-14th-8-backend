package com.dnd.moyeolak.global.metrics.stats.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 추이 차트용. range별 버킷(24h→1h, 7d→6h, 30d→1d) 시계열.
 */
public record ExternalApiTrendsResponse(
        String range,
        List<ApiTrend> apis
) {
    public record ApiTrend(
            String api,
            List<TrendPoint> points
    ) {
    }

    public record TrendPoint(
            LocalDateTime bucketStart,
            long calls,
            long failure,
            double errorRate,
            Double costUsd
    ) {
    }
}
