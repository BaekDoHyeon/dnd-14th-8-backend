package com.dnd.moyeolak.domain.meeting.dto;

import com.dnd.moyeolak.domain.meeting.enums.MeetingFlow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "모임 생성 요청")
public record CreateMeetingRequest(
        @Schema(description = "참가자 수", example = "5")
        @Min(value = 2, message = "참가자 수는 최소 2명 이상이어야 합니다")
        int participantCount,

        @Schema(description = "로컬스토리지 키", example = "meeting-key-123")
        @NotBlank(message = "로컬스토리지 키는 필수입니다")
        String localStorageKey,

        @Schema(description = "참가자 이름", example = "홍길동")
        @NotBlank(message = "참가자 이름은 필수입니다")
        String participantName,

        @Schema(description = "최초 생성 플로우", example = "SCHEDULE")
        MeetingFlow flow
) {
    public CreateMeetingRequest(int participantCount, String localStorageKey, String participantName) {
        this(participantCount, localStorageKey, participantName, MeetingFlow.SCHEDULE);
    }

    public MeetingFlow flowOrDefault() {
        return flow == null ? MeetingFlow.SCHEDULE : flow;
    }
}
