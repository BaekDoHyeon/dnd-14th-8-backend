package com.dnd.moyeolak.global.metrics;

import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import com.dnd.moyeolak.global.metrics.repository.ExternalApiMetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsSnapshotJobTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-22T01:00:00Z"), KST);
    private static final int RETENTION_DAYS = 30;

    private MetricsSnapshotReader reader;
    private ExternalApiMetricSnapshotRepository repository;
    private MetricsSnapshotJob job;

    @BeforeEach
    void setUp() {
        reader = mock(MetricsSnapshotReader.class);
        repository = mock(ExternalApiMetricSnapshotRepository.class);
        job = new MetricsSnapshotJob(reader, repository, FIXED_CLOCK, RETENTION_DAYS);
    }

    @Test
    @DisplayName("capture는 API별 스냅샷을 현재 시각과 함께 저장한다")
    @SuppressWarnings("unchecked")
    void capturesSnapshotPerApi() {
        when(reader.read()).thenReturn(Map.of(
                ExternalApi.ODSAY,
                new MetricSnapshotData(ExternalApi.ODSAY, 6, 3, 3, 4, 100, 200,
                        Map.of("5xx", 2L, "body_error", 1L), 0, 0),
                ExternalApi.GOOGLE_ROUTES,
                new MetricSnapshotData(ExternalApi.GOOGLE_ROUTES, 10, 10, 0, 0, 50, 90,
                        Map.of(), 3, 7)
        ));

        job.capture();

        ArgumentCaptor<List<ExternalApiMetricSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<ExternalApiMetricSnapshot> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        LocalDateTime expectedCapturedAt = LocalDateTime.now(FIXED_CLOCK);

        ExternalApiMetricSnapshot odsay = saved.stream()
                .filter(s -> s.getApi().equals("odsay")).findFirst().orElseThrow();
        assertThat(odsay.getCapturedAt()).isEqualTo(expectedCapturedAt);
        assertThat(odsay.getCallsTotal()).isEqualTo(6);
        assertThat(odsay.getRateLimitedTotal()).isEqualTo(4);
        assertThat(odsay.getErrorBreakdown()).containsEntry("5xx", 2L);

        ExternalApiMetricSnapshot google = saved.stream()
                .filter(s -> s.getApi().equals("google_routes")).findFirst().orElseThrow();
        assertThat(google.getComputeRoutesUnits()).isEqualTo(3);
        assertThat(google.getRouteMatrixUnits()).isEqualTo(7);
    }

    @Test
    @DisplayName("cleanup은 보관기간(30일)보다 오래된 스냅샷을 삭제한다")
    void cleanupDeletesOldSnapshots() {
        job.cleanup();

        verify(repository).deleteByCapturedAtBefore(
                LocalDateTime.now(FIXED_CLOCK).minusDays(RETENTION_DAYS));
    }
}
