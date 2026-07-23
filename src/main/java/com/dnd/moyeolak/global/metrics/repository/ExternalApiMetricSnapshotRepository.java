package com.dnd.moyeolak.global.metrics.repository;

import com.dnd.moyeolak.global.metrics.entity.ExternalApiMetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ExternalApiMetricSnapshotRepository
        extends JpaRepository<ExternalApiMetricSnapshot, Long> {

    List<ExternalApiMetricSnapshot> findByApiAndCapturedAtBetweenOrderByCapturedAtAsc(
            String api, LocalDateTime from, LocalDateTime to);

    List<ExternalApiMetricSnapshot> findByCapturedAtBetweenOrderByCapturedAtAsc(
            LocalDateTime from, LocalDateTime to);

    ExternalApiMetricSnapshot findFirstByApiOrderByCapturedAtDesc(String api);

    // 파생 삭제 쿼리는 트랜잭션이 필요하다. @Scheduled cleanup()이 트랜잭션 없이 호출하므로 여기서 보장한다.
    @Transactional
    long deleteByCapturedAtBefore(LocalDateTime threshold);
}
