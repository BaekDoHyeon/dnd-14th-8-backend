package com.dnd.moyeolak.domain.location.controller;

import com.dnd.moyeolak.domain.location.docs.CreateLocationVoteApiDocs;
import com.dnd.moyeolak.domain.location.docs.GetMidpointRecommendationsApiDocs;
import com.dnd.moyeolak.domain.location.docs.GetPersonalRouteApiDocs;
import com.dnd.moyeolak.domain.location.docs.ListLocationVoteApiDocs;
import com.dnd.moyeolak.domain.location.docs.NearbyPlaceSearchApiDocs;
import com.dnd.moyeolak.domain.location.docs.UpdateLocationVoteApiDocs;
import com.dnd.moyeolak.domain.location.dto.CreateLocationVoteRequest;
import com.dnd.moyeolak.domain.location.dto.LocationVoteResponse;
import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;
import com.dnd.moyeolak.domain.location.dto.PersonalRouteResponse;
import com.dnd.moyeolak.domain.location.enums.RouteMode;
import com.dnd.moyeolak.domain.location.dto.NearbyPlaceSearchResponse;
import com.dnd.moyeolak.domain.location.service.LocationVoteService;
import com.dnd.moyeolak.domain.location.service.MidpointRecommendationService;
import com.dnd.moyeolak.domain.location.service.PersonalRouteQueryService;
import com.dnd.moyeolak.domain.location.service.NearbyPlaceSearchService;
import com.dnd.moyeolak.domain.meeting.dto.UpdateLocationVoteRequest;
import com.dnd.moyeolak.global.ratelimit.ClientIpResolver;
import com.dnd.moyeolak.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Locations", description = "위치/장소 관련 API")
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationVoteService locationVoteService;
    private final MidpointRecommendationService midpointRecommendationService;
    private final PersonalRouteQueryService personalRouteQueryService;
    private final NearbyPlaceSearchService nearbyPlaceSearchService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/midpoint-recommendations")
    @GetMidpointRecommendationsApiDocs
    public ResponseEntity<ApiResponse<MidpointRecommendationResponse>> getMidpointRecommendations(
            @Parameter(description = "모임 ID", example = "test-meeting-001", required = true)
            @RequestParam String meetingId,
            @Parameter(description = "출발 시간 (미입력 시 현재 시각 기준)", example = "2026-02-18T10:30:00")
            @RequestParam(required = false) LocalDateTime departureTime,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolve(request);
        MidpointRecommendationResponse response =
                midpointRecommendationService.calculateMidpointRecommendations(meetingId, departureTime, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/midpoint-routes")
    @GetPersonalRouteApiDocs
    public ResponseEntity<ApiResponse<PersonalRouteResponse>> getPersonalRoute(
            @Parameter(description = "모임 ID", example = "test-meeting-001", required = true)
            @RequestParam String meetingId,
            @Parameter(description = "추천 역 ID (예: recommendations[0].stationId). test-meeting-001 더미 데이터 기준 김포공항역(5호선)=233", example = "233", required = true)
            @RequestParam Long stationId,
            @Parameter(description = "참여자 ID (예: test-meeting-001의 김민준=1)", example = "1", required = true)
            @RequestParam Long participantId,
            @Parameter(description = "출발 시각(ISO-8601). 비워두면 현재 시각 기준", example = "2026-02-18T10:30:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @Parameter(description = "`transit` / `driving` / `both`", example = "both")
            @RequestParam(required = false) String mode
    ) {
        RouteMode routeMode = RouteMode.from(mode);
        PersonalRouteResponse response = personalRouteQueryService.getPersonalRoute(
                meetingId, stationId, participantId, departureTime, routeMode
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/nearby-place-search")
    @NearbyPlaceSearchApiDocs
    public ResponseEntity<ApiResponse<NearbyPlaceSearchResponse>> nearbyPlaceSearch(
            @RequestParam String latitude,
            @RequestParam String longitude
    ) {
        NearbyPlaceSearchResponse response = nearbyPlaceSearchService.nearbyPlaceSearch(latitude, longitude);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/vote")
    @ListLocationVoteApiDocs
    public ResponseEntity<ApiResponse<?>> listLocationVote(
            @Parameter(description = "모임 ID", example = "test-meeting-001", required = true)
            @RequestParam String meetingId) {
        List<LocationVoteResponse> listLocationVote = locationVoteService.listLocationVote(meetingId);
        return ResponseEntity.ok(ApiResponse.success(listLocationVote));
    }

    @PutMapping("/vote/{locationVoteId}")
    @UpdateLocationVoteApiDocs
    public ResponseEntity<ApiResponse<Void>> updateLocationVote(
            @Parameter(description = "수정할 출발지 투표 ID", example = "1", required = true)
            @PathVariable Long locationVoteId,
            @Valid @RequestBody UpdateLocationVoteRequest request
    ) {
        locationVoteService.updateLocationVote(locationVoteId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/vote")
    @CreateLocationVoteApiDocs
    public ResponseEntity<ApiResponse<Long>> createLocationVote(@Valid @RequestBody CreateLocationVoteRequest request) {
        Long locationVoteId = locationVoteService.createLocationVote(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(locationVoteId));
    }

    @DeleteMapping("/vote/{locationVoteId}")
    @Operation(summary = "출발지 삭제", description = "등록된 출발지를 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteLocationVote(
            @Parameter(description = "삭제할 출발지 투표 ID", example = "1", required = true)
            @PathVariable Long locationVoteId) {
        locationVoteService.deleteLocationVote(locationVoteId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
