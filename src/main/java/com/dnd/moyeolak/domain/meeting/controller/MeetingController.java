package com.dnd.moyeolak.domain.meeting.controller;

import com.dnd.moyeolak.domain.meeting.dto.CreateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleResponse;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleVoteResultResponse;
import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.dto.MyMeetingResponse;
import com.dnd.moyeolak.domain.meeting.dto.UpdateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Meeting", description = "모임 관련 API")
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping("/all")
    @Operation(summary = "[TEST] 모임 전체 조회", description = """
            모든 모임의 ID를 조회합니다.
            현재 DB에 저장되어 있는 모임 ID를 가져오기 위한 테스트 용도로만 사용됩니다.
            """)
    public ResponseEntity<ApiResponse<List<String>>> getAllMeetings() {
        List<String> allMeetings = meetingService.findAllMeetings();
        return ResponseEntity.ok(ApiResponse.success(allMeetings));
    }

    @GetMapping("/stats")
    @Operation(summary = "랜딩 통계 조회", description = "랜딩 화면에 표시할 오늘 생성된 모임 수를 조회합니다.")
    public ResponseEntity<ApiResponse<LandingStatsResponse>> getLandingStats() {
        LandingStatsResponse stats = meetingService.getLandingStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/me")
    @Operation(summary = "내 모임 목록 조회", description = "브라우저 고유 키로 생성/참여 완료한 모임 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<MyMeetingResponse>>> getMyMeetings(
            @RequestParam String localStorageKey
    ) {
        List<MyMeetingResponse> myMeetings = meetingService.findMyMeetings(localStorageKey);
        return ResponseEntity.ok(ApiResponse.success(myMeetings));
    }

    @GetMapping("/{meetingId}/schedules")
    @Operation(summary = "모임 일정 조회", description = "특정 모임의 일정을 조회합니다.")
    public ResponseEntity<ApiResponse<GetMeetingScheduleResponse>> getMeetingSchedules(@PathVariable String meetingId) {
        GetMeetingScheduleResponse meetingSchedules = meetingService.getMeetingSchedules(meetingId);
        return ResponseEntity.ok(ApiResponse.success(meetingSchedules));
    }

    @GetMapping("/{meetingId}/schedule-vote/results")
    @Operation(summary = "모임 일정 투표 결과 조회", description = "특정 모임의 일정 투표 결과를 조회합니다.")
    public ResponseEntity<ApiResponse<GetMeetingScheduleVoteResultResponse>> getMeetingScheduleVoteResults(
            @PathVariable String meetingId
    ) {
        GetMeetingScheduleVoteResultResponse voteResults = meetingService.getMeetingScheduleVoteResults(meetingId);
        return ResponseEntity.ok(ApiResponse.success(voteResults));
    }

    @PostMapping
    @Operation(summary = "모임 생성", description = "새로운 모임을 생성합니다.")
    public ResponseEntity<ApiResponse<String>> createMeeting(@RequestBody CreateMeetingRequest request) {
        String meetingId = meetingService.createMeeting(request);
        return ResponseEntity.ok(ApiResponse.success(meetingId));
    }

    @PutMapping
    @Operation(summary = "모임 수정", description = "모임의 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<Void>> updateMeeting(@Valid @RequestBody UpdateMeetingRequest request) {
        meetingService.updateMeeting(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
