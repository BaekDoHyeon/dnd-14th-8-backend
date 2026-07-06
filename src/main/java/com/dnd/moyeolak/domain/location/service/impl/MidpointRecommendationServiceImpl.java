package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.*;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.MidpointRecommendationService;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.client.mapglot.MapGlotRouteClient;
import com.dnd.moyeolak.global.exception.BusinessException;
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
    private final MapGlotRouteClient mapGlotRouteClient;
    private final OdsayTransitRouteClient odsayTransitRouteClient;

    private static final int SEARCH_RADIUS_METERS = 5000;
    private static final int MAX_CANDIDATE_STATIONS = 10;
    private static final int REFINED_CANDIDATE_STATIONS = 5;
    private static final int TOP_RECOMMENDATIONS = 3;
    // OdsayClient의 Semaphore 허용치(5)에 맞춘 병렬도 — 더 늘려도 세마포어에서 대기만 한다
    private static final int TRANSIT_EVALUATION_THREADS = 5;

    @Override
    @Cacheable(value = "midpointRecommendations", key = "#meetingId + '_' + #departureTime")
    public MidpointRecommendationResponse calculateMidpointRecommendations(String meetingId, LocalDateTime departureTime) {
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

        // 4. MapGlot driving 결과로 ODsay 정밀 평가 후보를 줄인다.
        List<StationDrivingCandidate> refinedCandidates = selectDrivingCandidates(votes, candidateStations, centerPoint);

        // 5. ODsay 대중교통 결과로 최종 순위를 산정한다.
        List<StationRecommendationDto> recommendations = evaluateStations(votes, refinedCandidates, centerPoint);

        return new MidpointRecommendationResponse(
                centerPoint, recommendations, departureTime,
                votes.size(), meeting.getParticipantCount()
        );
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

    private List<StationDrivingCandidate> selectDrivingCandidates(
            List<LocationVote> votes,
            List<Station> stations,
            CenterPointDto centerPoint
    ) {
        List<StationDrivingCandidate> candidates = new ArrayList<>();

        for (Station station : stations) {
            List<DrivingRouteResult> drivingRoutes = votes.stream()
                    .map(vote -> mapGlotRouteClient.calculateDriving(
                            vote.getDepartureLat().doubleValue(),
                            vote.getDepartureLng().doubleValue(),
                            station.getLatitude(),
                            station.getLongitude()
                    ))
                    .toList();

            int distanceFromCenter = haversineDistance(
                    centerPoint.latitude(), centerPoint.longitude(),
                    station.getLatitude(), station.getLongitude()
            );
            candidates.add(new StationDrivingCandidate(station, drivingRoutes, distanceFromCenter));
        }

        boolean hasReachableDrivingRoute = candidates.stream()
                .flatMap(candidate -> candidate.drivingRoutes().stream())
                .anyMatch(DrivingRouteResult::reachable);

        Comparator<StationDrivingCandidate> comparator = hasReachableDrivingRoute
                ? Comparator.comparingDouble(StationDrivingCandidate::avgDrivingDuration)
                    .thenComparingInt(StationDrivingCandidate::unreachableDrivingRouteCount)
                    .thenComparingInt(StationDrivingCandidate::distanceFromCenter)
                : Comparator.comparingInt(StationDrivingCandidate::distanceFromCenter);

        return candidates.stream()
                .sorted(comparator)
                .limit(REFINED_CANDIDATE_STATIONS)
                .toList();
    }

    private List<StationRecommendationDto> evaluateStations(
            List<LocationVote> votes,
            List<StationDrivingCandidate> candidates,
            CenterPointDto centerPoint
    ) {
        List<List<TransitRouteResult>> transitResults = calculateTransitRoutes(votes, candidates);

        List<EvaluatedStation> results = new ArrayList<>();

        for (int candidateIdx = 0; candidateIdx < candidates.size(); candidateIdx++) {
            StationDrivingCandidate candidate = candidates.get(candidateIdx);
            Station station = candidate.station();
            List<RouteDto> routes = new ArrayList<>();
            int unreachableTransitRouteCount = 0;
            int reachableTransitDurationSum = 0;
            int reachableTransitRouteCount = 0;

            for (int voteIdx = 0; voteIdx < votes.size(); voteIdx++) {
                LocationVote vote = votes.get(voteIdx);
                TransitRouteResult transitRoute = transitResults.get(candidateIdx).get(voteIdx);
                DrivingRouteResult drivingRoute = candidate.drivingRouteAt(voteIdx);

                if (transitRoute.reachable()) {
                    reachableTransitDurationSum += transitRoute.durationMinutes();
                    reachableTransitRouteCount++;
                } else {
                    unreachableTransitRouteCount++;
                }

                String departureName = resolveDepartureName(vote);

                Long participantId = vote.getParticipant() != null ? vote.getParticipant().getId() : null;

                routes.add(RouteDto.builder()
                        .participantId(participantId)
                        .departureName(departureName)
                        .departureAddress(vote.getDepartureLocation())
                        .transitDuration(transitRoute.durationMinutes())
                        .transitDistance(transitRoute.distanceMeters())
                        .drivingDuration(drivingRoute.durationSeconds() / 60)
                        .drivingDistance(drivingRoute.distanceMeters())
                        .build());
            }

            if (reachableTransitRouteCount == 0) {
                continue;
            }

            double avgTransitDuration = (double) reachableTransitDurationSum / reachableTransitRouteCount;

            StationRecommendationDto recommendation = StationRecommendationDto.builder()
                .rank(0)
                .stationId(station.getId())
                .stationName(station.getName())
                .line(station.getLine())
                .latitude(station.getLatitude())
                .longitude(station.getLongitude())
                .distanceFromCenter(candidate.distanceFromCenter())
                .avgTransitDuration(avgTransitDuration)
                .routes(routes)
                .build();
            results.add(new EvaluatedStation(recommendation, unreachableTransitRouteCount));
        }

        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.ODSAY_API_ERROR);
        }

        // avgTransitDuration 오름차순 정렬 → Top 3 선정 → 순위 부여
        List<EvaluatedStation> sorted = results.stream()
                .sorted(Comparator.comparingDouble((EvaluatedStation result) -> result.recommendation().avgTransitDuration())
                        .thenComparingInt(EvaluatedStation::unreachableTransitRouteCount)
                        .thenComparingInt(result -> result.recommendation().distanceFromCenter()))
                .limit(TOP_RECOMMENDATIONS)
                .toList();

        return IntStream.range(0, sorted.size())
                .mapToObj(i -> StationRecommendationDto.builder()
                        .rank(i + 1)
                        .stationId(sorted.get(i).recommendation().stationId())
                        .stationName(sorted.get(i).recommendation().stationName())
                        .line(sorted.get(i).recommendation().line())
                        .latitude(sorted.get(i).recommendation().latitude())
                        .longitude(sorted.get(i).recommendation().longitude())
                        .distanceFromCenter(sorted.get(i).recommendation().distanceFromCenter())
                        .avgTransitDuration(sorted.get(i).recommendation().avgTransitDuration())
                        .routes(sorted.get(i).recommendation().routes())
                        .build())
                .toList();
    }

    private List<List<TransitRouteResult>> calculateTransitRoutes(
            List<LocationVote> votes,
            List<StationDrivingCandidate> candidates
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(TRANSIT_EVALUATION_THREADS);
        try {
            List<List<CompletableFuture<TransitRouteResult>>> futures = candidates.stream()
                    .map(candidate -> votes.stream()
                            .map(vote -> CompletableFuture.supplyAsync(
                                    () -> odsayTransitRouteClient.calculate(vote, candidate.station()),
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

    private record StationDrivingCandidate(
            Station station,
            List<DrivingRouteResult> drivingRoutes,
            int distanceFromCenter
    ) {

        private double avgDrivingDuration() {
            return drivingRoutes.stream()
                    .filter(DrivingRouteResult::reachable)
                    .mapToInt(DrivingRouteResult::durationSeconds)
                    .average()
                    .orElse(Double.MAX_VALUE);
        }

        private int unreachableDrivingRouteCount() {
            return (int) drivingRoutes.stream()
                    .filter(route -> !route.reachable())
                    .count();
        }

        private DrivingRouteResult drivingRouteAt(int index) {
            if (index >= drivingRoutes.size()) {
                return DrivingRouteResult.unreachable();
            }
            return drivingRoutes.get(index);
        }
    }

    private record EvaluatedStation(
            StationRecommendationDto recommendation,
            int unreachableTransitRouteCount
    ) {}
}
