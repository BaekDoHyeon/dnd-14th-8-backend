package com.dnd.moyeolak.global.metrics.repository;

import com.dnd.moyeolak.global.metrics.ExternalApi;
import com.dnd.moyeolak.global.metrics.MetricSnapshotData;
import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never",
        // 스케줄 스냅샷 잡이 테스트 중 발화해 데이터를 오염시키지 않도록 초기 지연을 크게 둔다
        "external-api.metrics.snapshot-initial-delay-ms=600000"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ExternalApiMetricSnapshotRepositoryTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:15-3.3")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("moyeolak")
            .withUsername("moyeolak")
            .withPassword("moyeolak");

    @Autowired
    private ExternalApiMetricSnapshotRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private ExternalApiMetricSnapshot snapshot(ExternalApi api, LocalDateTime at, Map<String, Long> errors) {
        return ExternalApiMetricSnapshot.of(at,
                new MetricSnapshotData(api, 10, 8, 2, 1, 100, 200, errors, 0, 0));
    }

    @Test
    @DisplayName("error_breakdown이 jsonb로 저장되고 Map으로 왕복된다")
    void persistsErrorBreakdownAsJsonb() {
        ExternalApiMetricSnapshot saved = repository.save(
                snapshot(ExternalApi.ODSAY, LocalDateTime.of(2026, 7, 22, 10, 0),
                        Map.of("5xx", 2L, "timeout", 1L)));

        ExternalApiMetricSnapshot found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getErrorBreakdown())
                .containsEntry("5xx", 2L)
                .containsEntry("timeout", 1L);
        assertThat(found.getApi()).isEqualTo("odsay");
    }

    @Test
    @DisplayName("API·기간으로 조회하면 captured_at 오름차순으로 반환된다")
    void findsByApiAndRangeOrdered() {
        repository.save(snapshot(ExternalApi.ODSAY, LocalDateTime.of(2026, 7, 22, 9, 0), Map.of()));
        repository.save(snapshot(ExternalApi.ODSAY, LocalDateTime.of(2026, 7, 22, 10, 0), Map.of()));
        repository.save(snapshot(ExternalApi.KAKAO_LOCAL, LocalDateTime.of(2026, 7, 22, 9, 30), Map.of()));

        List<ExternalApiMetricSnapshot> result =
                repository.findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(
                        "odsay",
                        LocalDateTime.of(2026, 7, 22, 8, 0),
                        LocalDateTime.of(2026, 7, 22, 11, 0));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCapturedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 9, 0));
        assertThat(result.get(1).getCapturedAt()).isEqualTo(LocalDateTime.of(2026, 7, 22, 10, 0));
    }

    @Test
    @DisplayName("보관기간 초과 스냅샷을 삭제한다")
    void deletesOldSnapshots() {
        repository.save(snapshot(ExternalApi.ODSAY, LocalDateTime.of(2026, 6, 1, 0, 0), Map.of()));
        repository.save(snapshot(ExternalApi.ODSAY, LocalDateTime.of(2026, 7, 22, 0, 0), Map.of()));

        long deleted = repository.deleteByCapturedAtBefore(LocalDateTime.of(2026, 7, 1, 0, 0));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(1);
    }
}
