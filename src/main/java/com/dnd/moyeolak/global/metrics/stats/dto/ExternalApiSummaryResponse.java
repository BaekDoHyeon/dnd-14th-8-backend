package com.dnd.moyeolak.global.metrics.stats.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 요약 카드용. 각 API의 "오늘"(KST 자정~현재) 집계.
 */
public record ExternalApiSummaryResponse(
        LocalDateTime asOf,
        List<ApiSummary> apis
) {
    public record ApiSummary(
            String api,
            long calls,
            long success,
            long failure,
            double successRate,
            long rateLimited,
            double p95Ms,
            Long dailyQuota,
            Long quotaRemaining,
            Double todayCostUsd
    ) {
    }
}
