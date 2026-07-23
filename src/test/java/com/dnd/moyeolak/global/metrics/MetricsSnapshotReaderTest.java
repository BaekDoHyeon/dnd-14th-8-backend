package com.dnd.moyeolak.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsSnapshotReaderTest {

    private MeterRegistry registry;
    private ExternalApiMetrics metrics;
    private MetricsSnapshotReader reader;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ExternalApiMetrics(registry);
        reader = new MetricsSnapshotReader(registry);
    }

    @Test
    @DisplayName("API별 성공/실패/호출수/레이트리밋/에러분해/레이턴시를 집계한다")
    void aggregatesPerApi() {
        metrics.recordSuccess(ExternalApi.ODSAY, Duration.ofMillis(100));
        metrics.recordSuccess(ExternalApi.ODSAY, Duration.ofMillis(100));
        metrics.recordSuccess(ExternalApi.ODSAY, Duration.ofMillis(100));
        metrics.recordFailure(ExternalApi.ODSAY, "5xx", Duration.ofMillis(200));
        metrics.recordFailure(ExternalApi.ODSAY, "5xx", Duration.ofMillis(200));
        metrics.recordFailure(ExternalApi.ODSAY, "body_error", Duration.ofMillis(50));
        metrics.recordRateLimited(ExternalApi.ODSAY);
        metrics.recordRateLimited(ExternalApi.ODSAY);
        metrics.recordRateLimited(ExternalApi.ODSAY);
        metrics.recordRateLimited(ExternalApi.ODSAY);

        Map<ExternalApi, MetricSnapshotData> result = reader.read();
        MetricSnapshotData data = result.get(ExternalApi.ODSAY);

        assertThat(data.success()).isEqualTo(3);
        assertThat(data.failure()).isEqualTo(3);
        assertThat(data.calls()).isEqualTo(6);
        assertThat(data.rateLimited()).isEqualTo(4);
        assertThat(data.errorBreakdown()).containsEntry("5xx", 2L).containsEntry("body_error", 1L);
        assertThat(data.p95Ms()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Google 과금 단위는 sku별로 분리 집계된다")
    void aggregatesGoogleBillableUnitsBySku() {
        metrics.recordGoogleBillableUnits(GoogleSku.ROUTE_MATRIX, 10);
        metrics.recordGoogleBillableUnits(GoogleSku.ROUTE_MATRIX, 5);
        metrics.recordGoogleBillableUnits(GoogleSku.COMPUTE_ROUTES, 3);

        MetricSnapshotData data = reader.read().get(ExternalApi.GOOGLE_ROUTES);

        assertThat(data.routeMatrixUnits()).isEqualTo(15);
        assertThat(data.computeRoutesUnits()).isEqualTo(3);
    }

    @Test
    @DisplayName("호출 기록이 없는 API는 0으로 채워진 스냅샷을 반환한다")
    void returnsZeroSnapshotForUnusedApi() {
        MetricSnapshotData data = reader.read().get(ExternalApi.KAKAO_LOCAL);

        assertThat(data.calls()).isZero();
        assertThat(data.failure()).isZero();
        assertThat(data.rateLimited()).isZero();
        assertThat(data.errorBreakdown()).isEmpty();
    }
}
