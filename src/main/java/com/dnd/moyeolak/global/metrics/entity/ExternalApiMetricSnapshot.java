package com.dnd.moyeolak.global.metrics.entity;

import com.dnd.moyeolak.global.metrics.MetricSnapshotData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 특정 시점의 외부 API 누적 지표 스냅샷. 트렌드는 연속 스냅샷 간 델타로 계산한다.
 */
@Entity
@Getter
@Table(
        name = "external_api_metric_snapshot",
        indexes = @Index(
                name = "idx_external_api_metric_snapshot_api_captured",
                columnList = "api, captured_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalApiMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(nullable = false, length = 32)
    private String api;

    @Column(name = "calls_total", nullable = false)
    private long callsTotal;

    @Column(name = "success_total", nullable = false)
    private long successTotal;

    @Column(name = "failure_total", nullable = false)
    private long failureTotal;

    @Column(name = "rate_limited_total", nullable = false)
    private long rateLimitedTotal;

    @Column(name = "latency_p50_ms", nullable = false)
    private double latencyP50Ms;

    @Column(name = "latency_p95_ms", nullable = false)
    private double latencyP95Ms;

    @Column(name = "compute_routes_units", nullable = false)
    private long computeRoutesUnits;

    @Column(name = "route_matrix_units", nullable = false)
    private long routeMatrixUnits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_breakdown", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> errorBreakdown;

    private ExternalApiMetricSnapshot(LocalDateTime capturedAt, MetricSnapshotData data) {
        this.capturedAt = capturedAt;
        this.api = data.api().tag();
        this.callsTotal = data.calls();
        this.successTotal = data.success();
        this.failureTotal = data.failure();
        this.rateLimitedTotal = data.rateLimited();
        this.latencyP50Ms = data.p50Ms();
        this.latencyP95Ms = data.p95Ms();
        this.computeRoutesUnits = data.computeRoutesUnits();
        this.routeMatrixUnits = data.routeMatrixUnits();
        this.errorBreakdown = data.errorBreakdown();
    }

    public static ExternalApiMetricSnapshot of(LocalDateTime capturedAt, MetricSnapshotData data) {
        return new ExternalApiMetricSnapshot(capturedAt, data);
    }
}
