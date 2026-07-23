package com.dnd.moyeolak.global.metrics.stats;

import com.dnd.moyeolak.global.metrics.ExternalApi;
import com.dnd.moyeolak.global.metrics.MetricSnapshotData;
import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import com.dnd.moyeolak.global.metrics.repository.ExternalApiMetricSnapshotRepository;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalApiStatsServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-22T04:00:00Z"), KST);

    private ExternalApiMetricSnapshotRepository repository;
    private ExternalApiStatsService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExternalApiMetricSnapshotRepository.class);
        service = new ExternalApiStatsService(repository, new ExternalApiStatsProperties(), CLOCK);
        // 기본: 스냅샷 없음
        when(repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(anyString(), any(), any()))
                .thenReturn(List.of());
    }

    private ExternalApiMetricSnapshot snap(ExternalApi api, LocalDateTime at, long calls, long success,
                                           long failure, long rateLimited, double p95, long cr, long rm) {
        return ExternalApiMetricSnapshot.of(at, new MetricSnapshotData(
                api, calls, success, failure, rateLimited, 0, p95, Map.of(), cr, rm));
    }

    @Test
    @DisplayName("요약은 오늘 구간의 델타로 호출/성공률/한도잔여를 계산한다")
    void summarizesOdsayWithDeltaAndQuota() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 22, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(eq("odsay"), any(), any()))
                .thenReturn(List.of(
                        snap(ExternalApi.ODSAY, t1, 100, 98, 2, 5, 180, 0, 0),
                        snap(ExternalApi.ODSAY, t2, 130, 127, 3, 8, 200, 0, 0)));
        when(repository.findFirstByApiOrderByCapturedAtDesc("odsay"))
                .thenReturn(snap(ExternalApi.ODSAY, t2, 130, 127, 3, 8, 200, 0, 0));

        ApiSummary odsay = service.summary().apis().stream()
                .filter(a -> a.api().equals("odsay")).findFirst().orElseThrow();

        assertThat(odsay.calls()).isEqualTo(30);
        assertThat(odsay.success()).isEqualTo(29);
        assertThat(odsay.failure()).isEqualTo(1);
        assertThat(odsay.rateLimited()).isEqualTo(3);
        assertThat(odsay.successRate()).isCloseTo(96.67, within(0.1));
        assertThat(odsay.p95Ms()).isEqualTo(200);
        assertThat(odsay.dailyQuota()).isEqualTo(1000);
        assertThat(odsay.quotaRemaining()).isEqualTo(970);
        assertThat(odsay.todayCostUsd()).isNull();
    }

    @Test
    @DisplayName("Google은 한도 대신 SKU별 과금 단위 델타로 비용을 계산한다")
    void summarizesGoogleWithCost() {
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 22, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(eq("google_routes"), any(), any()))
                .thenReturn(List.of(
                        snap(ExternalApi.GOOGLE_ROUTES, t1, 5, 5, 0, 0, 90, 10, 100),
                        snap(ExternalApi.GOOGLE_ROUTES, t2, 12, 12, 0, 0, 90, 13, 150)));

        ApiSummary google = service.summary().apis().stream()
                .filter(a -> a.api().equals("google_routes")).findFirst().orElseThrow();

        // computeRoutes 델타 3 * 0.005 + routeMatrix 델타 50 * 0.005 = 0.265
        assertThat(google.todayCostUsd()).isCloseTo(0.265, within(1e-6));
        assertThat(google.dailyQuota()).isNull();
        assertThat(google.quotaRemaining()).isNull();
    }

    @Test
    @DisplayName("스냅샷이 없는 API는 0으로 채워지고 한도 잔여는 한도와 같다")
    void summarizesEmptyApi() {
        ApiSummary kakao = service.summary().apis().stream()
                .filter(a -> a.api().equals("kakao_local")).findFirst().orElseThrow();

        assertThat(kakao.calls()).isZero();
        assertThat(kakao.successRate()).isZero();
        assertThat(kakao.quotaRemaining()).isEqualTo(100000);
    }
}
