package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.*;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.enums.MidpointResultType;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.MidpointRecommendationService;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.client.google.GoogleRoutesClient;
import com.dnd.moyeolak.global.client.google.dto.LatLng;
import com.dnd.moyeolak.global.client.kakao.KakaoDirectionsClient;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.ratelimit.MidpointRecommendationUsageLimiter;
import com.dnd.moyeolak.global.response.ErrorCode;
import com.dnd.moyeolak.global.station.entity.Station;
import com.dnd.moyeolak.global.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MidpointRecommendationServiceImpl implements MidpointRecommendationService {

    private final MeetingService meetingService;
    private final LocationVoteRepository locationVoteRepository;
    private final StationRepository stationRepository;
    private final GoogleRoutesClient googleRoutesClient;
    private final KakaoDirectionsClient kakaoDirectionsClient;
    private final MidpointRecommendationUsageLimiter usageLimiter;

    private static final int SEARCH_RADIUS_METERS = 5000;
    private static final int MAX_CANDIDATE_STATIONS = 10;
    private static final int TOP_RECOMMENDATIONS = 3;
    private static final int NEARBY_MAX_ROUTE_DURATION_MINUTES = 15;
    private static final int NEARBY_MAX_DEPARTURE_DISTANCE_METERS = 2000;
    private static final int WALKABLE_STATION_DISTANCE_METERS = 700;
    private static final int WALKING_SPEED_METERS_PER_MINUTE = 80;
    // KakaoDirectionsClient의 Semaphore 허용치(5)에 맞춘 병렬도 — 더 늘려도 세마포어에서 대기만 한다
    private static final int DRIVING_ENRICHMENT_THREADS = 5;

    @Override
    @Cacheable(value = "midpointRecommendations", key = "#meetingId + '_' + #departureTime")
    public MidpointRecommendationResponse calculateMidpointRecommendations(
            String meetingId,
            LocalDateTime departureTime,
            String clientIp
    ) {
        // 1. 출발지 데이터 조회
        Meeting meeting = meetingService.get(meetingId);
        LocationPoll locationPoll = meeting.getLocationPoll();
        if (locationPoll == null) {
            throw new BusinessException(ErrorCode.LOCATION_POLL_NOT_FOUND);
        }

        List<LocationVote> votes = locationVoteRepository.findByLocationPoll_Id(locationPoll.getId());
        if (votes.size() < 2) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_LOCATION_VOTES,
                    new ParticipantCountDto(votes.size(), meeting.getParticipantCount())
            );
        }

        // 2. PostGIS로 무게중심 계산
        CenterPointDto centerPoint = calculateCentroid(votes);
        log.info("무게중심 계산 완료: lat={}, lng={}", centerPoint.latitude(), centerPoint.longitude());

        // 3. PostGIS로 근처 지하철역 검색
        List<Station> candidateStations = stationRepository.findNearbyStations(
                centerPoint.latitude(),
                centerPoint.longitude(),
                SEARCH_RADIUS_METERS,
                MAX_CANDIDATE_STATIONS
        );
        if (candidateStations.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_NEARBY_STATIONS);
        }
        log.info("후보 지하철역 {}개 검색 완료", candidateStations.size());

        usageLimiter.checkAndIncrease(meetingId, clientIp);

        // 4. Google 매트릭스 1회로 전 후보역의 대중교통 시간 계산
        List<List<TransitRouteResult>> transitMatrix = googleRoutesClient.computeTransitMatrix(
                votes.stream()
                        .map(v -> new LatLng(v.getDepartureLat().doubleValue(), v.getDepartureLng().doubleValue()))
                        .toList(),
                candidateStations.stream()
                        .map(s -> new LatLng(s.getLatitude(), s.getLongitude()))
                        .toList(),
                departureTime
        );

        // 5. 평균 대중교통 시간으로 Top 3 선정
        List<StationEvaluation> topStations = selectTopStations(votes, candidateStations, transitMatrix, centerPoint);

        // 6. Top 3에만 Kakao 자동차 경로 보강 후 응답 조립
        List<StationRecommendationDto> recommendations = buildRecommendations(votes, topStations, departureTime);
        MidpointResultType resultType = resolveResultType(votes, recommendations);

        return new MidpointRecommendationResponse(
                centerPoint, recommendations, departureTime,
                votes.size(), meeting.getParticipantCount(), resultType
        );
    }

    private MidpointResultType resolveResultType(List<LocationVote> votes, List<StationRecommendationDto> recommendations) {
        if (votes.size() < 2 || recommendations.isEmpty()) {
            return MidpointResultType.NORMAL;
        }

        StationRecommendationDto topStation = recommendations.getFirst();
        boolean allRoutesReachable = topStation.routes().stream()
                .allMatch(RouteDto::transitReachable);
        boolean allRoutesShort = topStation.routes().stream()
                .allMatch(route -> route.transitDuration() <= NEARBY_MAX_ROUTE_DURATION_MINUTES);
        boolean departuresClose = areDeparturesClose(votes);

        if (allRoutesReachable
                && allRoutesShort
                && departuresClose) {
            return MidpointResultType.NEARBY_DEPARTURES;
        }

        return MidpointResultType.NORMAL;
    }

    private boolean areDeparturesClose(List<LocationVote> votes) {
        for (int i = 0; i < votes.size(); i++) {
            LocationVote source = votes.get(i);
            for (int j = i + 1; j < votes.size(); j++) {
                LocationVote target = votes.get(j);
                int distance = haversineDistance(
                        source.getDepartureLat().doubleValue(),
                        source.getDepartureLng().doubleValue(),
                        target.getDepartureLat().doubleValue(),
                        target.getDepartureLng().doubleValue()
                );
                if (distance > NEARBY_MAX_DEPARTURE_DISTANCE_METERS) {
                    return false;
                }
            }
        }
        return true;
    }

    private CenterPointDto calculateCentroid(List<LocationVote> votes) {
        String[] wktPoints = votes.stream()
                .map(v -> "SRID=4326;POINT(" +
                        v.getDepartureLng().doubleValue() + " " +
                        v.getDepartureLat().doubleValue() + ")")
                .toArray(String[]::new);

        try {
            Object[] result = stationRepository.calculateCentroid(wktPoints);
            if (result != null && result.length > 0) {
                Object[] row = (Object[]) result[0];
                double lat = ((Number) row[0]).doubleValue();
                double lng = ((Number) row[1]).doubleValue();
                return new CenterPointDto(lat, lng);
            }
        } catch (Exception e) {
            log.warn("PostGIS 무게중심 계산 실패, 산술 평균으로 대체: {}", e.getMessage());
        }

        // PostGIS 실패 시 산술 평균으로 대체
        double avgLat = votes.stream()
                .mapToDouble(v -> v.getDepartureLat().doubleValue())
                .average()
                .orElse(0);
        double avgLng = votes.stream()
                .mapToDouble(v -> v.getDepartureLng().doubleValue())
                .average()
                .orElse(0);
        return new CenterPointDto(avgLat, avgLng);
    }

    private List<StationEvaluation> selectTopStations(
            List<LocationVote> votes,
            List<Station> stations,
            List<List<TransitRouteResult>> transitMatrix,
            CenterPointDto centerPoint
    ) {
        List<StationEvaluation> evaluations = new ArrayList<>();

        for (int stationIdx = 0; stationIdx < stations.size(); stationIdx++) {
            Station station = stations.get(stationIdx);
            List<TransitRouteResult> transitRoutes = new ArrayList<>();
            for (int voteIdx = 0; voteIdx < votes.size(); voteIdx++) {
                LocationVote vote = votes.get(voteIdx);
                TransitRouteResult transitRoute = transitMatrix.get(voteIdx).get(stationIdx);
                transitRoutes.add(resolveTransitRoute(vote, station, transitRoute));
            }

            List<TransitRouteResult> reachableRoutes = transitRoutes.stream()
                    .filter(TransitRouteResult::reachable)
                    .toList();
            if (reachableRoutes.isEmpty()) {
                continue;
            }

            double avgTransitDuration = reachableRoutes.stream()
                    .mapToInt(TransitRouteResult::durationMinutes)
                    .average()
                    .orElse(Double.MAX_VALUE);
            int distanceFromCenter = haversineDistance(
                    centerPoint.latitude(), centerPoint.longitude(),
                    station.getLatitude(), station.getLongitude()
            );

            evaluations.add(new StationEvaluation(
                    station,
                    transitRoutes,
                    avgTransitDuration,
                    transitRoutes.size() - reachableRoutes.size(),
                    distanceFromCenter
            ));
        }

        if (evaluations.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_REACHABLE_STATIONS);
        }

        Comparator<StationEvaluation> comparator = Comparator.comparingInt(StationEvaluation::unreachableTransitRouteCount);
        if (areDeparturesClose(votes)) {
            comparator = comparator
                    .thenComparingInt(StationEvaluation::distanceFromCenter)
                    .thenComparingDouble(StationEvaluation::avgTransitDuration);
        } else {
            comparator = comparator
                    .thenComparingDouble(StationEvaluation::avgTransitDuration)
                    .thenComparingInt(StationEvaluation::distanceFromCenter);
        }

        // 도달 불가 참가자가 적은 역이 우선 — 일부만 갈 수 있는 역이 짧은 평균만으로 1위가 되지 않도록
        return evaluations.stream()
                .sorted(comparator)
                .limit(TOP_RECOMMENDATIONS)
                .toList();
    }

    private TransitRouteResult resolveTransitRoute(LocationVote vote, Station station, TransitRouteResult transitRoute) {
        int distanceToStation = haversineDistance(
                vote.getDepartureLat().doubleValue(),
                vote.getDepartureLng().doubleValue(),
                station.getLatitude(),
                station.getLongitude()
        );
        if (distanceToStation > WALKABLE_STATION_DISTANCE_METERS) {
            return transitRoute;
        }

        int walkingDuration = Math.max(1, (int) Math.ceil(distanceToStation / (double) WALKING_SPEED_METERS_PER_MINUTE));
        if (transitRoute.reachable() && transitRoute.durationMinutes() <= walkingDuration) {
            return transitRoute;
        }

        return new TransitRouteResult(walkingDuration, distanceToStation, true);
    }

    private List<StationRecommendationDto> buildRecommendations(
            List<LocationVote> votes,
            List<StationEvaluation> topStations,
            LocalDateTime departureTime
    ) {
        List<List<DrivingRouteResult>> drivingRoutes = calculateDrivingRoutes(votes, topStations, departureTime);

        return IntStream.range(0, topStations.size())
                .mapToObj(rank -> {
                    StationEvaluation evaluation = topStations.get(rank);
                    Station station = evaluation.station();
                    List<RouteDto> routes = new ArrayList<>();

                    for (int voteIdx = 0; voteIdx < votes.size(); voteIdx++) {
                        LocationVote vote = votes.get(voteIdx);
                        TransitRouteResult transitRoute = evaluation.transitRoutes().get(voteIdx);
                        DrivingRouteResult drivingRoute = drivingRoutes.get(rank).get(voteIdx);
                        Long participantId = vote.getParticipant() != null ? vote.getParticipant().getId() : null;

                        routes.add(RouteDto.builder()
                                .participantId(participantId)
                                .departureName(resolveDepartureName(vote))
                                .departureAddress(vote.getDepartureLocation())
                                .transitDuration(transitRoute.durationMinutes())
                                .transitDistance(transitRoute.distanceMeters())
                                .transitReachable(transitRoute.reachable())
                                .drivingDuration(drivingRoute.durationSeconds() / 60)
                                .drivingDistance(drivingRoute.distanceMeters())
                                .drivingReachable(drivingRoute.reachable())
                                .build());
                    }

                    return StationRecommendationDto.builder()
                            .rank(rank + 1)
                            .stationId(station.getId())
                            .stationName(station.getName())
                            .line(station.getLine())
                            .latitude(station.getLatitude())
                            .longitude(station.getLongitude())
                            .distanceFromCenter(evaluation.distanceFromCenter())
                            .avgTransitDuration(evaluation.avgTransitDuration())
                            .routes(routes)
                            .build();
                })
                .toList();
    }

    private List<List<DrivingRouteResult>> calculateDrivingRoutes(
            List<LocationVote> votes,
            List<StationEvaluation> topStations,
            LocalDateTime departureTime
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(DRIVING_ENRICHMENT_THREADS);
        try {
            List<List<CompletableFuture<DrivingRouteResult>>> futures = topStations.stream()
                    .map(evaluation -> votes.stream()
                            .map(vote -> CompletableFuture.supplyAsync(
                                    () -> requestDrivingRoute(vote, evaluation.station(), departureTime),
                                    executor))
                            .toList())
                    .toList();

            return futures.stream()
                    .map(stationFutures -> stationFutures.stream()
                            .map(CompletableFuture::join)
                            .toList())
                    .toList();
        } finally {
            executor.shutdown();
        }
    }

    private DrivingRouteResult requestDrivingRoute(LocationVote vote, Station station, LocalDateTime departureTime) {
        KakaoDirectionsResponse.Summary summary = kakaoDirectionsClient.requestDrivingRoute(
                vote.getDepartureLat().doubleValue(),
                vote.getDepartureLng().doubleValue(),
                station.getLatitude(),
                station.getLongitude(),
                departureTime
        );
        if (summary == null) {
            return DrivingRouteResult.unreachable();
        }
        return new DrivingRouteResult(summary.duration(), summary.distance(), true);
    }

    private String resolveDepartureName(LocationVote vote) {
        if (vote.getDepartureName() != null && !vote.getDepartureName().isBlank()) {
            return vote.getDepartureName();
        }
        if (vote.getParticipant() != null && vote.getParticipant().getName() != null) {
            return vote.getParticipant().getName();
        }
        return "알 수 없음";
    }

    private int haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371e3;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return (int) (R * c);
    }

    private record StationEvaluation(
            Station station,
            List<TransitRouteResult> transitRoutes,
            double avgTransitDuration,
            int unreachableTransitRouteCount,
            int distanceFromCenter
    ) {}
}
