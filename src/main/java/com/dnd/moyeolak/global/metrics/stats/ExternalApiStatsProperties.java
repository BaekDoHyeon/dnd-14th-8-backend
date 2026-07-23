package com.dnd.moyeolak.global.metrics.stats;

import com.dnd.moyeolak.global.metrics.ExternalApi;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 외부 API 일일 한도 및 Google 과금 단가. 기본값은 2026-07 기준 조사값이며 계정 상태에 맞게 조정한다.
 * - ODsay Basic(무료): 1,000/일
 * - Kakao Local / Directions: 100,000/일 (계정별 확인 권장)
 * - Google Compute Routes: Basic $5/1,000 요청 = $0.005/요청
 * - Google Route Matrix: Basic $5/1,000 엘리먼트 = $0.005/엘리먼트
 */
@Getter
@Setter
@Component
@ConfigurationProperties("external-api.stats")
public class ExternalApiStatsProperties {

    private long odsayDailyQuota = 1000;
    private long kakaoLocalDailyQuota = 100000;
    private long kakaoDirectionsDailyQuota = 100000;
    private double googleComputeRoutesPerRequest = 0.005;
    private double googleRouteMatrixPerElement = 0.005;

    /** 해당 API의 일일 한도. 한도 개념이 없는 API(Google)는 null. */
    public Long dailyQuota(ExternalApi api) {
        return switch (api) {
            case ODSAY -> odsayDailyQuota;
            case KAKAO_LOCAL -> kakaoLocalDailyQuota;
            case KAKAO_DIRECTIONS -> kakaoDirectionsDailyQuota;
            case GOOGLE_ROUTES -> null;
        };
    }

    /** Google 과금 단위 델타로 추정 비용(USD)을 계산한다. */
    public double googleCostUsd(long computeRoutesUnits, long routeMatrixUnits) {
        return computeRoutesUnits * googleComputeRoutesPerRequest
                + routeMatrixUnits * googleRouteMatrixPerElement;
    }
}
