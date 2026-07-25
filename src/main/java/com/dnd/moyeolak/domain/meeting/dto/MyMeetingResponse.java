package com.dnd.moyeolak.domain.meeting.dto;

import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.enums.MeetingFlow;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.schedule.entity.SchedulePoll;
import com.dnd.moyeolak.global.enums.PollStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        boolean isHost,

        @Schema(description = "최근 모임에서 진입 가능한 플로우", example = "[\"SCHEDULE\", \"LOCATION\"]")
        List<MeetingFlow> availableFlows
) {
    public static MyMeetingResponse of(Meeting meeting, Participant myParticipant, String hostName) {
        return new MyMeetingResponse(
                meeting.getId(),
                hostName,
                meeting.getParticipantCount(),
                meeting.getCreatedAt(),
                myParticipant.isHost(),
                resolveAvailableFlows(meeting)
        );
    }

    private static List<MeetingFlow> resolveAvailableFlows(Meeting meeting) {
        boolean hasScheduleFlow = flowOrDefault(meeting) == MeetingFlow.SCHEDULE || hasScheduleUsage(meeting);
        boolean hasLocationFlow = flowOrDefault(meeting) == MeetingFlow.LOCATION || hasLocationUsage(meeting);

        List<MeetingFlow> flows = new ArrayList<>();
        if (hasScheduleFlow) {
            flows.add(MeetingFlow.SCHEDULE);
        }
        if (hasLocationFlow) {
            flows.add(MeetingFlow.LOCATION);
        }
        return flows.isEmpty() ? List.of(MeetingFlow.SCHEDULE) : flows;
    }

    private static MeetingFlow flowOrDefault(Meeting meeting) {
        return meeting.getInitialFlow() == null ? MeetingFlow.SCHEDULE : meeting.getInitialFlow();
    }

    private static boolean hasScheduleUsage(Meeting meeting) {
        SchedulePoll schedulePoll = meeting.getSchedulePoll();
        if (schedulePoll != null && schedulePoll.getPollStatus() == PollStatus.CONFIRMED) {
            return true;
        }

        return meeting.getParticipants().stream()
                .anyMatch(participant -> !participant.getScheduleVotes().isEmpty());
    }

    private static boolean hasLocationUsage(Meeting meeting) {
        LocationPoll locationPoll = meeting.getLocationPoll();
        if (locationPoll != null && !locationPoll.getLocationVotes().isEmpty()) {
            return true;
        }

        return meeting.getParticipants().stream()
                .anyMatch(participant -> !participant.getLocationVotes().isEmpty());
    }
}
