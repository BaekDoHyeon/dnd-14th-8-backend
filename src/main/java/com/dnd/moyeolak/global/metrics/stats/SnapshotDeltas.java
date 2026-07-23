package com.dnd.moyeolak.global.metrics.stats;

import java.util.List;

/**
 * 누적 카운터 스냅샷 값들의 구간 증가분(델타)을 합산한다.
 * 앱 재시작으로 카운터가 리셋되면 음수 델타가 발생하므로 0으로 클램프한다.
 */
public final class SnapshotDeltas {

    private SnapshotDeltas() {
    }

    public static long sum(List<Long> cumulativeValues) {
        long total = 0;
        for (int i = 1; i < cumulativeValues.size(); i++) {
            long delta = cumulativeValues.get(i) - cumulativeValues.get(i - 1);
            if (delta > 0) {
                total += delta;
            }
        }
        return total;
    }
}
