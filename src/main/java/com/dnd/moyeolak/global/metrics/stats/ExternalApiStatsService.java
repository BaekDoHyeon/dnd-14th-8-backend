package com.dnd.moyeolak.global.metrics.stats;

import com.dnd.moyeolak.global.metrics.ExternalApi;
import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import com.dnd.moyeolak.global.metrics.repository.ExternalApiMetricSnapshotRepository;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiTrendsResponse;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiTrendsResponse.ApiTrend;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiTrendsResponse.TrendPoint;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 스냅샷 테이블에서 관리자 대시보드용 요약/추이 통계를 집계한다.
 * 카운터는 누적값이므로 구간 델타(리셋 클램프)로 계산하고, p95는 최신 스냅샷의 롤링 윈도우 값을 쓴다.
 */
@Service
public class ExternalApiStatsService {

    private final ExternalApiMetricSnapshotRepository repository;
    private final ExternalApiStatsProperties properties;
    private final Clock clock;

    public ExternalApiStatsService(
            ExternalApiMetricSnapshotRepository repository,
            ExternalApiStatsProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public ExternalApiSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        List<ApiSummary> apis = Arrays.stream(ExternalApi.values())
                .map(api -> summarize(api, startOfDay, now))
                .toList();
        return new ExternalApiSummaryResponse(now, apis);
    }

    public ExternalApiTrendsResponse trends(StatsRange range) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = now.minus(range.window());
        List<ApiTrend> apis = Arrays.stream(ExternalApi.values())
                .map(api -> trend(api, range, from, now))
                .toList();
        return new ExternalApiTrendsResponse(range.code(), apis);
    }

    private ApiSummary summarize(ExternalApi api, LocalDateTime from, LocalDateTime to) {
        List<ExternalApiMetricSnapshot> snaps =
                repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(api.tag(), from, to);
        long calls = delta(snaps, ExternalApiMetricSnapshot::getCallsTotal);
        long success = delta(snaps, ExternalApiMetricSnapshot::getSuccessTotal);
        long failure = delta(snaps, ExternalApiMetricSnapshot::getFailureTotal);
        long rateLimited = delta(snaps, ExternalApiMetricSnapshot::getRateLimitedTotal);
        double successRate = calls == 0 ? 0.0 : round2(success * 100.0 / calls);

        Long quota = properties.dailyQuota(api);
        Long remaining = quota == null ? null : Math.max(0, quota - calls);
        Double cost = null;
        if (api == ExternalApi.GOOGLE_ROUTES) {
            cost = properties.googleCostUsd(
                    delta(snaps, ExternalApiMetricSnapshot::getComputeRoutesUnits),
                    delta(snaps, ExternalApiMetricSnapshot::getRouteMatrixUnits));
        }
        return new ApiSummary(api.tag(), calls, success, failure, successRate,
                rateLimited, latestP95(api), quota, remaining, cost);
    }

    private ApiTrend trend(ExternalApi api, StatsRange range, LocalDateTime from, LocalDateTime to) {
        List<ExternalApiMetricSnapshot> snaps =
                repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(api.tag(), from, to);
        List<TrendPoint> points = new ArrayList<>();
        Duration bucket = range.bucket();
        for (LocalDateTime start = from; start.isBefore(to); start = start.plus(bucket)) {
            LocalDateTime bucketStart = start;
            LocalDateTime bucketEnd = start.plus(bucket);
            List<ExternalApiMetricSnapshot> inBucket = snaps.stream()
                    .filter(s -> !s.getCapturedAt().isBefore(bucketStart)
                            && s.getCapturedAt().isBefore(bucketEnd))
                    .toList();
            long calls = delta(inBucket, ExternalApiMetricSnapshot::getCallsTotal);
            long failure = delta(inBucket, ExternalApiMetricSnapshot::getFailureTotal);
            double errorRate = calls == 0 ? 0.0 : round2(failure * 100.0 / calls);
            Double cost = api == ExternalApi.GOOGLE_ROUTES
                    ? properties.googleCostUsd(
                            delta(inBucket, ExternalApiMetricSnapshot::getComputeRoutesUnits),
                            delta(inBucket, ExternalApiMetricSnapshot::getRouteMatrixUnits))
                    : null;
            points.add(new TrendPoint(bucketStart, calls, failure, errorRate, cost));
        }
        return new ApiTrend(api.tag(), points);
    }

    private double latestP95(ExternalApi api) {
        ExternalApiMetricSnapshot latest = repository.findFirstByApiOrderByCapturedAtDesc(api.tag());
        return latest == null ? 0.0 : latest.getLatencyP95Ms();
    }

    private long delta(List<ExternalApiMetricSnapshot> snaps, ToLongFunction<ExternalApiMetricSnapshot> field) {
        return SnapshotDeltas.sum(snaps.stream().map(field::applyAsLong).toList());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
