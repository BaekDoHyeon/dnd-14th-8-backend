package com.dnd.moyeolak.global.client.google;

import com.dnd.moyeolak.domain.location.dto.TransitRouteDetailDto;
import com.dnd.moyeolak.domain.location.dto.TransitRouteResult;
import com.dnd.moyeolak.global.client.google.config.GoogleRoutesApiConfig;
import com.dnd.moyeolak.global.client.google.dto.ComputeRoutesRequest;
import com.dnd.moyeolak.global.client.google.dto.ComputeRoutesResponse;
import com.dnd.moyeolak.global.client.google.dto.LatLng;
import com.dnd.moyeolak.global.client.google.dto.RouteMatrixEntry;
import com.dnd.moyeolak.global.client.google.dto.RouteMatrixRequest;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class GoogleRoutesClient {

    private static final String MATRIX_URL =
            "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";
    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String TRAVEL_MODE_TRANSIT = "TRANSIT";
    // Routes API TRANSIT 매트릭스는 요청당 최대 100 엘리먼트(origins x destinations)
    private static final int MAX_MATRIX_ELEMENTS = 100;
    private static final String MATRIX_FIELD_MASK =
            "originIndex,destinationIndex,duration,distanceMeters,condition";
    private static final String ROUTE_FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.travelAdvisory.transitFare,"
                    + "routes.legs.steps.travelMode,routes.legs.steps.distanceMeters";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final GoogleRoutesApiConfig config;

    public GoogleRoutesClient(
            @Qualifier("googleRoutesRestTemplate") RestTemplate restTemplate,
            GoogleRoutesApiConfig config
    ) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public List<List<TransitRouteResult>> computeTransitMatrix(
            List<LatLng> origins,
            List<LatLng> destinations,
            LocalDateTime departureTime
    ) {
        if (origins.isEmpty() || destinations.isEmpty()) {
            return List.of();
        }
        TransitRouteResult[][] grid = new TransitRouteResult[origins.size()][destinations.size()];
        for (TransitRouteResult[] row : grid) {
            Arrays.fill(row, TransitRouteResult.unreachable());
        }

        int originsPerRequest = Math.max(1, MAX_MATRIX_ELEMENTS / destinations.size());
        for (int offset = 0; offset < origins.size(); offset += originsPerRequest) {
            List<LatLng> chunk = origins.subList(offset, Math.min(offset + originsPerRequest, origins.size()));
            for (RouteMatrixEntry entry : requestMatrix(chunk, destinations, departureTime)) {
                if (!entry.routeExists()) {
                    continue;
                }
                grid[offset + entry.originIndex()][entry.destinationIndex()] = new TransitRouteResult(
                        (int) Math.round(entry.durationSeconds() / 60.0),
                        entry.safeDistanceMeters(),
                        true
                );
            }
        }

        return Arrays.stream(grid).map(List::of).toList();
    }

    public TransitRouteDetailDto computeTransitRoute(
            LatLng origin,
            LatLng destination,
            LocalDateTime departureTime
    ) {
        ComputeRoutesRequest body = ComputeRoutesRequest.of(
                origin, destination, TRAVEL_MODE_TRANSIT, toRfc3339(departureTime));

        ComputeRoutesResponse response;
        try {
            response = restTemplate.postForObject(
                    ROUTES_URL, new HttpEntity<>(body, headers(ROUTE_FIELD_MASK)), ComputeRoutesResponse.class);
        } catch (RestClientException e) {
            log.error("Google Routes 단건 경로 호출 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_API_ERROR);
        }

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.error("Google Routes 단건 경로 응답이 비어있습니다.");
            throw new BusinessException(ErrorCode.GOOGLE_API_ERROR);
        }

        ComputeRoutesResponse.Route route = response.routes().getFirst();
        return new TransitRouteDetailDto(
                route.durationMinutes(),
                route.safeDistanceMeters(),
                route.fareWon(),
                route.transferCount(),
                route.walkDistanceMeters()
        );
    }

    private RouteMatrixEntry[] requestMatrix(
            List<LatLng> origins,
            List<LatLng> destinations,
            LocalDateTime departureTime
    ) {
        RouteMatrixRequest body = RouteMatrixRequest.of(
                origins, destinations, TRAVEL_MODE_TRANSIT, toRfc3339(departureTime));

        try {
            RouteMatrixEntry[] entries = restTemplate.postForObject(
                    MATRIX_URL, new HttpEntity<>(body, headers(MATRIX_FIELD_MASK)), RouteMatrixEntry[].class);
            if (entries == null) {
                log.error("Google Routes 매트릭스 응답이 비어있습니다.");
                throw new BusinessException(ErrorCode.GOOGLE_API_ERROR);
            }
            return entries;
        } catch (RestClientException e) {
            log.error("Google Routes 매트릭스 호출 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_API_ERROR);
        }
    }

    private HttpHeaders headers(String fieldMask) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", config.getGoogleApiKey());
        headers.set("X-Goog-FieldMask", fieldMask);
        return headers;
    }

    private String toRfc3339(LocalDateTime departureTime) {
        if (departureTime == null) {
            return null;
        }
        Instant instant = departureTime.atZone(KST).toInstant();
        // Google TRANSIT은 과거 departureTime을 400으로 거부한다 — 과거 시각은 "지금 출발"로 처리
        if (instant.isBefore(Instant.now())) {
            return null;
        }
        return instant.toString();
    }
}
