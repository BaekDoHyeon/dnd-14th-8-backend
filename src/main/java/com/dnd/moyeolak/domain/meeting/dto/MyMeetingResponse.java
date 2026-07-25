package com.dnd.moyeolak.domain.meeting.dto;

import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내 모임 목록 응답")
public record MyMeetingResponse(
        @Schema(description = "모임 ID", example = "abc123")
        String meetingId,

        @Schema(description = "방장 이름", example = "민수")
        String hostName,

        @Schema(description = "모임 참여 인원", example = "4")
        int participantCount,

        @Schema(description = "모임 생성 시각", example = "2026-07-23T14:10:00")
        LocalDateTime createdAt,

        @Schema(description = "내 방장 여부", example = "true")
        boolean isHost
) {
    public static MyMeetingResponse of(Meeting meeting, Participant myParticipant, String hostName) {
        return new MyMeetingResponse(
                meeting.getId(),
                hostName,
                meeting.getParticipantCount(),
                meeting.getCreatedAt(),
                myParticipant.isHost()
        );
    }
}
