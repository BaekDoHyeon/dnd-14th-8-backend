package com.dnd.moyeolak.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 외부 API 호출을 Micrometer로 계측하는 공유 헬퍼.
 * 각 클라이언트가 자신의 성공/실패 판정에 따라 명시적으로 기록한다
 * (ODsay의 999 센티넬, Kakao Directions의 null 등 상태코드로 잡히지 않는 실패 때문).
 */
@Component
public class ExternalApiMetrics {

    private static final String CALLS = "external.api.calls";
    private static final String LATENCY = "external.api.latency";
    private static final String RATE_LIMITED = "external.api.rate_limited";
    private static final String GOOGLE_UNITS = "external.api.google.billable_units";
    private static final String ERROR_TYPE_NONE = "none";

    private final MeterRegistry registry;

    public ExternalApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess(ExternalApi api, Duration latency) {
        registry.counter(CALLS,
                "api", api.tag(), "outcome", "success", "error_type", ERROR_TYPE_NONE).increment();
        timer(api).record(latency);
    }

    public void recordFailure(ExternalApi api, String errorType, Duration latency) {
        registry.counter(CALLS,
                "api", api.tag(), "outcome", "failure", "error_type", errorType).increment();
        timer(api).record(latency);
    }

    public void recordRateLimited(ExternalApi api) {
        registry.counter(RATE_LIMITED, "api", api.tag()).increment();
    }

    public void recordGoogleBillableUnits(GoogleSku sku, long count) {
        registry.counter(GOOGLE_UNITS, "sku", sku.tag()).increment(count);
    }

    private Timer timer(ExternalApi api) {
        return Timer.builder(LATENCY)
                .tag("api", api.tag())
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }
}
