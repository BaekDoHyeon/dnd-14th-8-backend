package com.dnd.moyeolak.global.metrics.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotDeltasTest {

    @Test
    @DisplayName("누적값의 연속 증가분을 합산한다")
    void sumsConsecutiveIncrements() {
        assertThat(SnapshotDeltas.sum(List.of(10L, 15L, 22L))).isEqualTo(12);
    }

    @Test
    @DisplayName("앱 재시작으로 누적값이 줄면(리셋) 해당 구간 델타를 0으로 클램프한다")
    void clampsNegativeDeltaOnReset() {
        // 10->15 (+5), 15->3 리셋(clamp 0), 3->8 (+5) = 10
        assertThat(SnapshotDeltas.sum(List.of(10L, 15L, 3L, 8L))).isEqualTo(10);
    }

    @Test
    @DisplayName("스냅샷이 1개 이하면 델타가 없어 0이다")
    void returnsZeroForInsufficientData() {
        assertThat(SnapshotDeltas.sum(List.of(42L))).isZero();
        assertThat(SnapshotDeltas.sum(List.of())).isZero();
    }
}
