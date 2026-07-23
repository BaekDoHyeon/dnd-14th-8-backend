package com.dnd.moyeolak.global.metrics;

import java.util.Map;

/**
 * 특정 시점 MeterRegistry에서 읽어낸 한 API의 누적 지표 값.
 * 카운터는 애플리케이션 구동 이후 누적값이며, 트렌드는 스냅샷 간 델타로 계산한다.
 */
public record MetricSnapshotData(
        ExternalApi api,
        long calls,
        long success,
        long failure,
        long rateLimited,
        double p50Ms,
        double p95Ms,
        Map<String, Long> errorBreakdown,
        long computeRoutesUnits,
        long routeMatrixUnits
) {
}
