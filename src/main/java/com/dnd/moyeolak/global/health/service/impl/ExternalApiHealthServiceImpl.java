package com.dnd.moyeolak.global.health.service.impl;

import com.dnd.moyeolak.domain.location.dto.TransitRouteDetailDto;
import com.dnd.moyeolak.global.client.google.GoogleRoutesClient;
import com.dnd.moyeolak.global.client.google.dto.LatLng;
import com.dnd.moyeolak.global.client.kakao.KakaoDirectionsClient;
import com.dnd.moyeolak.global.client.kakao.KakaoLocalClient;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
import com.dnd.moyeolak.global.client.kakao.dto.SubwayStation;
import com.dnd.moyeolak.global.client.odsay.OdsayClient;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.health.dto.ExternalApiHealthResponse;
import com.dnd.moyeolak.global.health.dto.ExternalApiHealthResponse.ApiCheckResult;
import com.dnd.moyeolak.global.health.service.ExternalApiHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * 로컬 개발 환경에서 외부 API 키/쿼터/연결 상태를 확인하는 진단용 서비스.
 * API마다 최소 비용 호출 1건씩만 수행한다 (Google Routes는 엘리먼트 단위 과금).
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class ExternalApiHealthServiceImpl implements ExternalApiHealthService {

    private static final double CITY_HALL_LAT = 37.5665;
    private static final double CITY_HALL_LNG = 126.9780;
    private static final double GANGNAM_STATION_LAT = 37.4979;
    private static final double GANGNAM_STATION_LNG = 127.0276;

    private static final int ODSAY_FAILURE_SENTINEL = 999;

    private final KakaoLocalClient kakaoLocalClient;
    private final KakaoDirectionsClient kakaoDirectionsClient;
    private final GoogleRoutesClient googleRoutesClient;
    private final OdsayClient odsayClient;

    @Override
    public ExternalApiHealthResponse checkExternalApis() {
        List<ApiCheckResult> results = List.of(
                check("Kakao Local", this::checkKakaoLocal),
                check("Kakao Directions", this::checkKakaoDirections),
                check("Google Routes", this::checkGoogleRoutes),
                check("ODsay", this::checkOdsay)
        );
        return ExternalApiHealthResponse.from(results);
    }

    private ApiCheckResult check(String name, Supplier<String> checker) {
        long start = System.currentTimeMillis();
        try {
            String detail = checker.get();
            long latency = System.currentTimeMillis() - start;
            log.info("[외부 API 헬스체크] {} OK ({}ms)", name, latency);
            return new ApiCheckResult(name, true, latency, detail);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("[외부 API 헬스체크] {} 실패 ({}ms): {}", name, latency, e.getMessage());
            return new ApiCheckResult(name, false, latency, e.getMessage());
        }
    }

    private String checkKakaoLocal() {
        List<SubwayStation> stations = kakaoLocalClient.findNearestSubwayStations(
                CITY_HALL_LAT, CITY_HALL_LNG, 1000, 1);
        if (stations.isEmpty()) {
            throw new IllegalStateException("시청 반경 1km 지하철역 조회 결과가 비어있음");
        }
        return "지하철역 조회 성공: " + stations.getFirst().name();
    }

    private String checkKakaoDirections() {
        KakaoDirectionsResponse.Summary summary = kakaoDirectionsClient.requestDrivingRoute(
                CITY_HALL_LAT, CITY_HALL_LNG, GANGNAM_STATION_LAT, GANGNAM_STATION_LNG, null);
        if (summary == null) {
            throw new IllegalStateException("자동차 경로 응답 없음 (로그에서 원인 확인)");
        }
        return "시청→강남역 자동차 경로 조회 성공: " + summary.duration() / 60 + "분";
    }

    private String checkGoogleRoutes() {
        TransitRouteDetailDto detail = googleRoutesClient.computeTransitRoute(
                new LatLng(CITY_HALL_LAT, CITY_HALL_LNG),
                new LatLng(GANGNAM_STATION_LAT, GANGNAM_STATION_LNG),
                null
        );
        return "시청→강남역 대중교통 경로 조회 성공: " + detail.durationMinutes() + "분";
    }

    private String checkOdsay() {
        OdsayPathInfo pathInfo = odsayClient.searchRoute(
                CITY_HALL_LAT, CITY_HALL_LNG, GANGNAM_STATION_LAT, GANGNAM_STATION_LNG);
        if (pathInfo.safeTotal() == ODSAY_FAILURE_SENTINEL) {
            throw new IllegalStateException("대중교통 경로 응답 없음 (로그에서 원인 확인)");
        }
        return "시청→강남역 대중교통 경로 조회 성공: " + pathInfo.safeTotal() + "분";
    }
}
