package com.dnd.moyeolak.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiMetricsTest {

    private MeterRegistry registry;
    private ExternalApiMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ExternalApiMetrics(registry);
    }

    @Test
    @DisplayName("성공 기록 시 outcome=success 카운터가 증가하고 레이턴시 타이머에 기록된다")
    void recordSuccessIncrementsCounterAndTimer() {
        metrics.recordSuccess(ExternalApi.ODSAY, Duration.ofMillis(120));

        double calls = registry.get("external.api.calls")
                .tags("api", "odsay", "outcome", "success")
                .counter().count();
        assertThat(calls).isEqualTo(1.0);

        long timerCount = registry.get("external.api.latency")
                .tags("api", "odsay")
                .timer().count();
        assertThat(timerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 기록 시 outcome=failure, error_type 태그 카운터가 증가하고 레이턴시도 기록된다")
    void recordFailureIncrementsCounterWithErrorTypeAndTimer() {
        metrics.recordFailure(ExternalApi.KAKAO_LOCAL, "5xx", Duration.ofMillis(300));

        double calls = registry.get("external.api.calls")
                .tags("api", "kakao_local", "outcome", "failure", "error_type", "5xx")
                .counter().count();
        assertThat(calls).isEqualTo(1.0);

        long timerCount = registry.get("external.api.latency")
                .tags("api", "kakao_local")
                .timer().count();
        assertThat(timerCount).isEqualTo(1);
    }

    @Test
    @DisplayName("레이트리밋 기록 시 external.api.rate_limited 카운터가 api 태그로 증가한다")
    void recordRateLimitedIncrementsCounter() {
        metrics.recordRateLimited(ExternalApi.ODSAY);
        metrics.recordRateLimited(ExternalApi.ODSAY);

        double count = registry.get("external.api.rate_limited")
                .tags("api", "odsay")
                .counter().count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Google 과금 단위 기록 시 sku 태그로 엘리먼트 수만큼 누적된다")
    void recordGoogleBillableUnitsAccumulatesBySku() {
        metrics.recordGoogleBillableUnits(GoogleSku.ROUTE_MATRIX, 10);
        metrics.recordGoogleBillableUnits(GoogleSku.ROUTE_MATRIX, 5);
        metrics.recordGoogleBillableUnits(GoogleSku.COMPUTE_ROUTES, 1);

        double matrix = registry.get("external.api.google.billable_units")
                .tags("sku", "route_matrix")
                .counter().count();
        assertThat(matrix).isEqualTo(15.0);

        double routes = registry.get("external.api.google.billable_units")
                .tags("sku", "compute_routes")
                .counter().count();
        assertThat(routes).isEqualTo(1.0);
    }
}
