package com.dnd.moyeolak.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MetricsSnapshotReader {

    private static final String CALLS = "external.api.calls";
    private static final String LATENCY = "external.api.latency";
    private static final String RATE_LIMITED = "external.api.rate_limited";
    private static final String GOOGLE_UNITS = "external.api.google.billable_units";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";
    private static final String ERROR_TYPE_NONE = "none";

    private static final String[] ERROR_TYPES = {
            ExternalApiErrorType.CLIENT_4XX,
            ExternalApiErrorType.SERVER_5XX,
            ExternalApiErrorType.TIMEOUT,
            ExternalApiErrorType.BODY_ERROR,
            ExternalApiErrorType.UNKNOWN
    };

    private final MeterRegistry registry;

    public MetricsSnapshotReader(MeterRegistry registry) {
        this.registry = registry;
    }

    public Map<ExternalApi, MetricSnapshotData> read() {
        Map<ExternalApi, MetricSnapshotData> snapshots = new LinkedHashMap<>();
        for (ExternalApi api : ExternalApi.values()) {
            long success = counter(CALLS,
                    "api", api.tag(), "outcome", OUTCOME_SUCCESS, "error_type", ERROR_TYPE_NONE);
            Map<String, Long> errorBreakdown = errorBreakdown(api);
            long failure = errorBreakdown.values().stream().mapToLong(Long::longValue).sum();
            long rateLimited = counter(RATE_LIMITED, "api", api.tag());
            TimerValues timerValues = timerValues(api);

            snapshots.put(api, new MetricSnapshotData(
                    api,
                    success + failure,
                    success,
                    failure,
                    rateLimited,
                    timerValues.p50Ms(),
                    timerValues.p95Ms(),
                    errorBreakdown,
                    googleUnits(api, GoogleSku.COMPUTE_ROUTES),
                    googleUnits(api, GoogleSku.ROUTE_MATRIX)
            ));
        }
        return snapshots;
    }

    private Map<String, Long> errorBreakdown(ExternalApi api) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (String errorType : ERROR_TYPES) {
            long count = counter(CALLS,
                    "api", api.tag(), "outcome", OUTCOME_FAILURE, "error_type", errorType);
            if (count > 0) {
                breakdown.put(errorType, count);
            }
        }
        return breakdown;
    }

    private long googleUnits(ExternalApi api, GoogleSku sku) {
        if (api != ExternalApi.GOOGLE_ROUTES) {
            return 0;
        }
        return counter(GOOGLE_UNITS, "sku", sku.tag());
    }

    private long counter(String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0L : Math.round(counter.count());
    }

    private TimerValues timerValues(ExternalApi api) {
        Timer timer = registry.find(LATENCY).tag("api", api.tag()).timer();
        if (timer == null || timer.count() == 0) {
            return new TimerValues(0.0, 0.0);
        }

        ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
        double p50 = percentileValue(percentiles, 0.5);
        double p95 = percentileValue(percentiles, 0.95);
        double fallback = timer.max(java.util.concurrent.TimeUnit.MILLISECONDS);

        return new TimerValues(
                p50 > 0.0 ? p50 : fallback,
                p95 > 0.0 ? p95 : fallback
        );
    }

    private double percentileValue(ValueAtPercentile[] percentiles, double percentile) {
        return Arrays.stream(percentiles)
                .filter(value -> Double.compare(value.percentile(), percentile) == 0)
                .mapToDouble(value -> value.value(java.util.concurrent.TimeUnit.MILLISECONDS))
                .findFirst()
                .orElse(0.0);
    }

    private record TimerValues(double p50Ms, double p95Ms) {
    }
}
