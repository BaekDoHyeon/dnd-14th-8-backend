package com.dnd.moyeolak.global.metrics;

import com.dnd.moyeolak.global.metrics.alert.QuotaAlertNotifier;
import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import com.dnd.moyeolak.global.metrics.repository.ExternalApiMetricSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주기적으로 MeterRegistry 누적값을 읽어 스냅샷 테이블에 적재하고,
 * 보관기간이 지난 스냅샷을 정리한다. 캡처 직후 quota 알림 여부를 검사한다.
 */
@Slf4j
@Component
public class MetricsSnapshotJob {

    private final MetricsSnapshotReader reader;
    private final ExternalApiMetricSnapshotRepository repository;
    private final QuotaAlertNotifier quotaAlertNotifier;
    private final Clock clock;
    private final int retentionDays;

    public MetricsSnapshotJob(
            MetricsSnapshotReader reader,
            ExternalApiMetricSnapshotRepository repository,
            QuotaAlertNotifier quotaAlertNotifier,
            Clock clock,
            @Value("${external-api.metrics.retention-days:30}") int retentionDays
    ) {
        this.reader = reader;
        this.repository = repository;
        this.quotaAlertNotifier = quotaAlertNotifier;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(
            fixedRateString = "${external-api.metrics.snapshot-interval-ms:300000}",
            initialDelayString = "${external-api.metrics.snapshot-initial-delay-ms:0}")
    public void capture() {
        LocalDateTime capturedAt = LocalDateTime.now(clock);
        List<ExternalApiMetricSnapshot> snapshots = reader.read().values().stream()
                .map(data -> ExternalApiMetricSnapshot.of(capturedAt, data))
                .toList();
        repository.saveAll(snapshots);
        log.debug("외부 API 지표 스냅샷 {}건 저장 ({})", snapshots.size(), capturedAt);
        quotaAlertNotifier.checkAndNotify();
    }

    @Scheduled(cron = "${external-api.metrics.cleanup-cron:0 30 4 * * *}")
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        long deleted = repository.deleteByCapturedAtBefore(threshold);
        if (deleted > 0) {
            log.info("보관기간({} 일) 초과 외부 API 스냅샷 {}건 삭제", retentionDays, deleted);
        }
    }
}
