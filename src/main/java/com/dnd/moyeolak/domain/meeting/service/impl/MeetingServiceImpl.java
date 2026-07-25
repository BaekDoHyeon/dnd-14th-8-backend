package com.dnd.moyeolak.domain.meeting.service.impl;

import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.meeting.dto.CreateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleResponse;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleVoteResultResponse;
import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.dto.MyMeetingResponse;
import com.dnd.moyeolak.domain.meeting.dto.UpdateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.domain.participant.dto.ParticipantResponse;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.repository.ParticipantRepository;
import com.dnd.moyeolak.domain.participant.service.ParticipantService;
import com.dnd.moyeolak.domain.schedule.entity.SchedulePoll;
import com.dnd.moyeolak.domain.schedule.entity.ScheduleVote;
import com.dnd.moyeolak.domain.schedule.service.ScheduleVoteService;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingServiceImpl implements MeetingService {

    private static final int RECENT_MEETING_LIMIT = 5;

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantService participantService;
    private final ScheduleVoteService scheduleVoteService;
    private final Clock clock;

    @Override
    @Transactional
    public String createMeeting(CreateMeetingRequest request) {
        Meeting meeting = Meeting.of(request.participantCount(), request.flowOrDefault());

        meeting.addParticipant(Participant.hostOf(meeting, request.localStorageKey(), request.participantName()));

        meeting.addPolls(SchedulePoll.defaultOf(meeting), LocationPoll.defaultOf(meeting));

        Meeting saveMeeting = meetingRepository.save(meeting);
        return saveMeeting.getId();
    }

    @Override
    @Transactional
    public void updateMeeting(UpdateMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        ParticipantResponse participantResponse
                = participantService.findByMeetingIdAndLocalStorageKey(request.meetingId(), request.localStorageKey());

        if (meeting.getParticipants().size() > request.participantCount()) {
            throw new BusinessException(ErrorCode.PARTICIPANT_COUNT_BELOW_CURRENT);
        }

        if (!participantResponse.isHost()) {
            throw new BusinessException(ErrorCode.MEETING_EDIT_FORBIDDEN);
        }

        meeting.update(request.participantCount());
    }

    @Override
    public Meeting get(String meetingId) {
        return findMeetingWithAllAssociations(meetingId);
    }

    @Override
    public GetMeetingScheduleResponse getMeetingSchedules(String meetingId) {
        Meeting meeting = findMeetingWithAllAssociations(meetingId);

        return GetMeetingScheduleResponse.from(meeting);
    }

    @Override
    public GetMeetingScheduleVoteResultResponse getMeetingScheduleVoteResults(String meetingId) {
        /*
         * 1. MeetingId 에 해당하는 모임의 정보를 조회합니다. (모임, 일정 투표, 참가자 정보 모두 조회)
         */
        Meeting meeting = findMeetingWithAllAssociations(meetingId);
        int participantCount = meeting.getParticipantCount();
        List<String> participantNames = meeting.getParticipants().stream().map(Participant::getName).toList();

        List<ScheduleVote> scheduleVotes = scheduleVoteService.findAllBySchedulePollId(meeting.getSchedulePoll().getId());

        /*
         * 2. 조회된 모임의 일정 투표 정보와 참가자 정보를 바탕으로, 일정 투표 결과를 계산하여 반환합니다.
          - 일정 투표 결과는 각 일정 옵션에 대해 몇 명의 참가자가 해당 일정을 선택했는지를 나타내는 형태로 구성됩니다.
         *  - 예시: 일정 옵션 A: 3명, 일정 옵션 B: 5명, 일정 옵션 C: 2명
         */
        return GetMeetingScheduleVoteResultResponse.of(participantCount, participantNames, scheduleVotes);
    }

    @Override
    public LandingStatsResponse getLandingStats() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime startInclusive = today.atStartOfDay();
        LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

        long todayCreatedMeetingCount = meetingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startInclusive,
                endExclusive
        );

        return new LandingStatsResponse(todayCreatedMeetingCount);
    }

    @Override
    public List<String> findAllMeetings() {
        return meetingRepository.findAllMeetingsId();
    }

    @Override
    public List<MyMeetingResponse> findMyMeetings(String localStorageKey) {
        if (localStorageKey == null || localStorageKey.isBlank()) {
            return List.of();
        }

        return participantRepository.findAllByLocalStorageKeyWithMeetingAndParticipants(localStorageKey).stream()
                .map(participant -> {
                    Meeting meeting = participant.getMeeting();
                    String hostName = meeting.getParticipants().stream()
                            .filter(Participant::isHost)
                            .findFirst()
                            .map(Participant::getName)
                            .orElse("방장");

                    return MyMeetingResponse.of(meeting, participant, hostName);
                })
                .toList();
    }

    @Override
    @Transactional
    public void deleteMeeting(String meetingId) {
        Meeting meeting = findMeetingWithAllAssociations(meetingId);
        meetingRepository.delete(meeting);
    }

    private Meeting findMeetingWithAllAssociations(String meetingId) {
        return meetingRepository.findByIdWithAllAssociations(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
    }
}
