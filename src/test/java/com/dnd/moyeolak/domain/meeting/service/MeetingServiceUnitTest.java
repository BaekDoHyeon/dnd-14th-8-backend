package com.dnd.moyeolak.domain.meeting.service;

import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleResponse;
import com.dnd.moyeolak.domain.meeting.dto.MyMeetingResponse;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.enums.MeetingFlow;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import com.dnd.moyeolak.domain.meeting.service.impl.MeetingServiceImpl;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.repository.ParticipantRepository;
import com.dnd.moyeolak.domain.schedule.entity.SchedulePoll;
import com.dnd.moyeolak.domain.schedule.entity.ScheduleVote;
import com.dnd.moyeolak.global.enums.PollStatus;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceUnitTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private Clock clock;

    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private MeetingServiceImpl meetingService;

    /**
     * 모임 일정 조회 관련 테스트
     */
    @Test
    @DisplayName("모임 일정 조회 시 참가자 수가 반환된다")
    void getMeetingSchedules_returnsParticipantCount() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

        // then
        assertThat(response.participantCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("모임 일정 조회 시 참가자 목록이 반환된다")
    void getMeetingSchedules_returnsParticipants() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

        // then
        assertThat(response.participants()).hasSize(4);
        assertThat(response.participants().get(0).name()).isEqualTo("홍길동");
        assertThat(response.participants().get(1).name()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("모임 일정 조회 시 일정 설정 정보가 반환된다")
    void getMeetingSchedules_returnsSchedulePollInfo() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

        // then
        assertThat(response.dateOptions()).isNotEmpty();
        assertThat(response.startTime()).isEqualTo("09:00");
        assertThat(response.endTime()).isEqualTo("24:00");
    }

    @Test
    @DisplayName("모임 일정 조회 시 일정 투표 데이터가 반환된다")
    void getMeetingSchedules_returnsScheduleVotes() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

        // then
        List<LocalDateTime> list = new ArrayList<>();
        response.participants().forEach(p -> list.addAll(p.votedDates()));
        assertThat(list).hasSize(0); // 초기에는 투표가 없으므로 0이어야 함
    }

    @Test
    @DisplayName("존재하지 않는 모임 조회 시 예외가 발생한다")
    void getMeetingSchedules_throwsExceptionWhenNotFound() {
        // given
        String meetingId = "non-existent-id";
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> meetingService.getMeetingSchedules(meetingId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MEETING_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("모임 일정 조회 시 투표한 참가자 수 0명과 전체 참가자 수 10명이 반환된다")
    void getMeetingSchedules_returnsVotedParticipantCount() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

        // then
        assertThat(response.votedParticipantCount()).isEqualTo(0);
        assertThat(response.participantCount()).isEqualTo(10);
    }

    /**
     * 모임 삭제 관련 테스트
     */
    @Test
    @DisplayName("모임 삭제 시 모임이 존재하면 정상 삭제된다")
    void deleteMeeting_deletesSuccessfullyWhenFound() {
        // given
        String meetingId = "test-meeting-id";
        Meeting meeting = createMeetingWithAllAssociations();
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

        // when
        meetingService.deleteMeeting(meetingId);

        // then
        verify(meetingRepository).delete(meeting);
    }

    @Test
    @DisplayName("모임 삭제 시 모임이 존재하지 않으면 예외가 발생한다")
    void deleteMeeting_throwsExceptionWhenNotFound() {
        // given
        String meetingId = "non-existent-id";
        when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.empty());

        // when && then
        assertThatThrownBy(() -> meetingService.deleteMeeting(meetingId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MEETING_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("랜딩 통계 조회 시 오늘 생성된 모임 수가 반환된다")
    void getLandingStats_returnsTodayCreatedMeetingCount() {
        // given
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-18T03:15:00Z"));
        when(clock.getZone()).thenReturn(zoneId);
        when(meetingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 7, 18, 0, 0),
                LocalDateTime.of(2026, 7, 19, 0, 0)
        )).thenReturn(128L);

        // when
        var response = meetingService.getLandingStats();

        // then
        assertThat(response.todayCreatedMeetingCount()).isEqualTo(128L);
    }

    @Test
    @DisplayName("내 모임 조회 시 localStorageKey에 연결된 모임 요약 목록을 반환한다")
    void findMyMeetings_returnsMeetingSummaries() {
        // given
        String localStorageKey = "my-session-key";
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 14, 10);
        Meeting meeting = Meeting.of(4);
        ReflectionTestUtils.setField(meeting, "id", "meeting-id");
        ReflectionTestUtils.setField(meeting, "createdAt", createdAt);

        Participant host = Participant.hostOf(meeting, localStorageKey, "민수");
        meeting.addParticipant(host);
        meeting.addParticipant(Participant.of(meeting, "other-session-key", "지영"));

        when(participantRepository.findAllByLocalStorageKeyWithMeetingAndParticipants(localStorageKey))
                .thenReturn(List.of(host));

        // when
        List<MyMeetingResponse> responses = meetingService.findMyMeetings(localStorageKey);

        // then
        assertThat(responses).hasSize(1);
        MyMeetingResponse response = responses.getFirst();
        assertThat(response.meetingId()).isEqualTo("meeting-id");
        assertThat(response.hostName()).isEqualTo("민수");
        assertThat(response.participantCount()).isEqualTo(4);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.isHost()).isTrue();
        assertThat(response.availableFlows()).containsExactly(MeetingFlow.SCHEDULE);
    }

    @Test
    @DisplayName("내 모임 조회 시 참여자로 속한 모임은 isHost false로 반환한다")
    void findMyMeetings_returnsParticipantRole() {
        // given
        String localStorageKey = "participant-session-key";
        Meeting meeting = Meeting.of(3);
        ReflectionTestUtils.setField(meeting, "id", "meeting-id");
        ReflectionTestUtils.setField(meeting, "createdAt", LocalDateTime.of(2026, 7, 23, 15, 30));

        Participant host = Participant.hostOf(meeting, "host-session-key", "지영");
        Participant participant = Participant.of(meeting, localStorageKey, "민수");
        meeting.addParticipant(host);
        meeting.addParticipant(participant);

        when(participantRepository.findAllByLocalStorageKeyWithMeetingAndParticipants(localStorageKey))
                .thenReturn(List.of(participant));

        // when
        List<MyMeetingResponse> responses = meetingService.findMyMeetings(localStorageKey);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().hostName()).isEqualTo("지영");
        assertThat(responses.getFirst().isHost()).isFalse();
    }

    @Test
    @DisplayName("내 모임 조회 시 중간지점으로 생성된 모임은 위치 플로우만 반환한다")
    void findMyMeetings_returnsLocationFlowForLocationOnlyMeeting() {
        // given
        String localStorageKey = "location-host-session-key";
        Meeting meeting = Meeting.of(3, MeetingFlow.LOCATION);
        ReflectionTestUtils.setField(meeting, "id", "location-meeting-id");
        ReflectionTestUtils.setField(meeting, "createdAt", LocalDateTime.of(2026, 7, 26, 12, 0));

        Participant host = Participant.hostOf(meeting, localStorageKey, "민수");
        meeting.addParticipant(host);
        meeting.addPolls(SchedulePoll.defaultOf(meeting), LocationPoll.defaultOf(meeting));

        when(participantRepository.findAllByLocalStorageKeyWithMeetingAndParticipants(localStorageKey))
                .thenReturn(List.of(host));

        // when
        List<MyMeetingResponse> responses = meetingService.findMyMeetings(localStorageKey);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().availableFlows()).containsExactly(MeetingFlow.LOCATION);
    }

    @Test
    @DisplayName("내 모임 조회 시 일정과 위치 투표가 모두 있으면 두 플로우를 반환한다")
    void findMyMeetings_returnsBothFlowsWhenScheduleAndLocationVotesExist() {
        // given
        String localStorageKey = "both-flow-session-key";
        Meeting meeting = Meeting.of(4, MeetingFlow.SCHEDULE);
        ReflectionTestUtils.setField(meeting, "id", "both-flow-meeting-id");
        ReflectionTestUtils.setField(meeting, "createdAt", LocalDateTime.of(2026, 7, 26, 13, 0));

        Participant host = Participant.hostOf(meeting, localStorageKey, "민수");
        meeting.addParticipant(host);
        SchedulePoll schedulePoll = SchedulePoll.defaultOf(meeting);
        LocationPoll locationPoll = LocationPoll.defaultOf(meeting);
        meeting.addPolls(schedulePoll, locationPoll);

        host.addScheduleVote(ScheduleVote.of(schedulePoll, List.of(LocalDateTime.of(2026, 7, 27, 10, 0))));
        host.addLocationVote(LocationVote.of(
                locationPoll,
                host,
                "서울특별시 강남구",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276)
        ));

        when(participantRepository.findAllByLocalStorageKeyWithMeetingAndParticipants(localStorageKey))
                .thenReturn(List.of(host));

        // when
        List<MyMeetingResponse> responses = meetingService.findMyMeetings(localStorageKey);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().availableFlows())
                .containsExactly(MeetingFlow.SCHEDULE, MeetingFlow.LOCATION);
    }

    private Meeting createMeetingWithAllAssociations() {
        // Meeting 생성
        Meeting meeting = Meeting.of(10);

        // 참가자 추가
        Participant participant1 = Participant.of(meeting, "key1", "홍길동");
        Participant participant2 = Participant.of(meeting, "key2", "김철수");
        Participant participant3 = Participant.of(meeting, "key3", "백두팔");
        Participant participant4 = Participant.of(meeting, "key4", "이영희");
        meeting.addParticipant(participant1);
        meeting.addParticipant(participant2);
        meeting.addParticipant(participant3);
        meeting.addParticipant(participant4);

        // SchedulePoll, LocationPoll 생성 및 설정
        SchedulePoll schedulePoll = SchedulePoll.defaultOf(meeting);
        LocationPoll locationPoll = LocationPoll.defaultOf(meeting);

        meeting.addPolls(schedulePoll, locationPoll);

        return meeting;
    }
}
