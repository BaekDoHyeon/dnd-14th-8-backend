package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.CreateLocationVoteRequest;
import com.dnd.moyeolak.domain.location.dto.LocationVoteResponse;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.impl.LocationVoteServiceImpl;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.service.ParticipantService;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationVoteUnitTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ParticipantService participantService;

    @Mock
    private LocationVoteRepository locationVoteRepository;

    @InjectMocks
    private LocationVoteServiceImpl locationService;

    @Nested
    @DisplayName("수동 추가 (localStorageKey 없음)")
    class ManualAdd {

        @Test
        @DisplayName("수동 추가 시 LocationVote만 저장된다")
        void createLocationVote_manualAdd_savesLocationVoteOnly() {
            // given
            String meetingId = "meeting-id-123";
            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(Meeting.ofId(meetingId)));

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    null,  // localStorageKey가 null → 수동 추가
                    "홍길동",
                    "서울시 강남구",
                    "37.4979502",
                    "127.0276368"
            );

            // when
            locationService.createLocationVote(request);

            // then
            ArgumentCaptor<LocationVote> captor = ArgumentCaptor.forClass(LocationVote.class);
            verify(locationVoteRepository).save(captor.capture());

            LocationVote savedVote = captor.getValue();
            assertThat(savedVote.getDepartureName()).isEqualTo("홍길동");
            assertThat(savedVote.getDepartureLocation()).isEqualTo("서울시 강남구");
            assertThat(savedVote.getDepartureLat()).isEqualByComparingTo(new BigDecimal("37.4979502"));
            assertThat(savedVote.getDepartureLng()).isEqualByComparingTo(new BigDecimal("127.0276368"));

            verify(participantService, never()).save(any());
        }

        @Test
        @DisplayName("수동 추가 시 빈 문자열 localStorageKey도 수동 추가로 처리된다")
        void createLocationVote_emptyLocalStorageKey_savesLocationVoteOnly() {
            // given
            String meetingId = "meeting-id-123";
            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(Meeting.ofId(meetingId)));

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    "",  // 빈 문자열 → 수동 추가
                    "김철수",
                    "서울시 홍대입구",
                    "37.5571010",
                    "126.9236450"
            );

            // when
            locationService.createLocationVote(request);

            // then
            verify(locationVoteRepository).save(any(LocationVote.class));
            verify(participantService, never()).save(any());
        }
    }

    @Nested
    @DisplayName("실 참여자 추가 (localStorageKey 있음, 신규 참여자)")
    class NewParticipantAdd {

        @Test
        @DisplayName("신규 참여자 추가 시 Participant와 LocationVote가 함께 저장된다")
        void createLocationVote_participantAdd_savesParticipantWithLocationVote() {
            // given
            String meetingId = "meeting-id-123";
            Meeting meeting = Meeting.ofId(meetingId);
            // 기존 참여자 없음 → meeting.getParticipants()는 비어있음

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    "local-storage-key-abc",  // localStorageKey 존재, 신규 참여자
                    "이영희",
                    "서울시 왕십리",
                    "37.5614080",
                    "127.0379670"
            );

            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            // when
            locationService.createLocationVote(request);

            // then
            ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
            verify(participantService).save(captor.capture());

            Participant savedParticipant = captor.getValue();
            assertThat(savedParticipant.getName()).isEqualTo("이영희");
            assertThat(savedParticipant.getLocalStorageKey()).isEqualTo("local-storage-key-abc");
            assertThat(savedParticipant.getLocationVotes()).hasSize(1);

            LocationVote locationVote = savedParticipant.getLocationVotes().get(0);
            assertThat(locationVote.getDepartureLocation()).isEqualTo("서울시 왕십리");
            assertThat(locationVote.getDepartureLat()).isEqualByComparingTo(new BigDecimal("37.5614080"));
            assertThat(locationVote.getDepartureLng()).isEqualByComparingTo(new BigDecimal("127.0379670"));

            verify(locationVoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("신규 참여자 추가 시 Meeting ID가 올바르게 세팅된다")
        void createLocationVote_participantAdd_setsMeetingId() {
            // given
            String meetingId = "meeting-id-456";
            Meeting meeting = Meeting.ofId(meetingId);

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    "local-storage-key-xyz",
                    "박민수",
                    "서울시 서초구",
                    "37.4837121",
                    "127.0324112"
            );

            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            // when
            locationService.createLocationVote(request);

            // then
            ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
            verify(participantService).save(captor.capture());

            Participant savedParticipant = captor.getValue();
            assertThat(savedParticipant.getMeeting().getId()).isEqualTo(meetingId);
        }
    }

    @Nested
    @DisplayName("모임장 첫 출발지 등록 (기존 호스트 참여자)")
    class HostFirstLocationVote {

        @Test
        @DisplayName("모임장이 처음 출발지 등록 시 LocationVote가 기존 Participant에 연결된다")
        void createLocationVote_hostFirstVote_savesVoteLinkedToExistingParticipant() {
            // given
            String meetingId = "meeting-id-789";
            Meeting meeting = Meeting.ofId(meetingId);
            Participant host = Participant.hostOf(meeting, "host-key", "모임장");
            meeting.addParticipant(host);
            // 호스트는 아직 출발지를 등록하지 않은 상태 (locationVotes 비어있음)

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    "host-key",
                    "모임장",
                    "서울시 마포구",
                    "37.5549340",
                    "126.9137540"
            );

            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            // when
            locationService.createLocationVote(request);

            // then
            verify(participantService, never()).save(any());
            verify(locationVoteRepository).save(argThat(locationVote ->
                    locationVote.getDepartureLocation().equals("서울시 마포구")
            ));
            assertThat(host.getLocationVotes()).hasSize(1);
            assertThat(host.getLocationVotes().get(0).getDepartureLocation()).isEqualTo("서울시 마포구");
        }
    }

    @Nested
    @DisplayName("서비스 지역 검증")
    class ServiceAreaValidation {

        @Test
        @DisplayName("서비스 지역(수도권) 밖 좌표(독도)로 등록 시 OUT_OF_SERVICE_AREA 예외가 발생한다")
        void createLocationVote_outOfServiceArea_throwsException() {
            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    "meeting-id-123",
                    null,
                    "홍길동",
                    "경북 울릉군 독도",
                    "37.2426",
                    "131.8597"
            );

            assertThatThrownBy(() -> locationService.createLocationVote(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_SERVICE_AREA);

            verify(locationVoteRepository, never()).save(any());
            verify(participantService, never()).save(any());
        }

        @Test
        @DisplayName("서비스 지역 남쪽 밖 좌표(제주)로 등록 시 OUT_OF_SERVICE_AREA 예외가 발생한다")
        void createLocationVote_southOfServiceArea_throwsException() {
            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    "meeting-id-123",
                    null,
                    "홍길동",
                    "제주특별자치도 제주시",
                    "33.4996",
                    "126.5312"
            );

            assertThatThrownBy(() -> locationService.createLocationVote(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_SERVICE_AREA);
        }

        @Test
        @DisplayName("숫자가 아닌 좌표로 등록 시 INVALID_FORMAT 예외가 발생한다")
        void createLocationVote_nonNumericCoordinates_throwsException() {
            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    "meeting-id-123",
                    null,
                    "홍길동",
                    "서울시 강남구",
                    "abc",
                    "127.0276368"
            );

            assertThatThrownBy(() -> locationService.createLocationVote(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FORMAT);
        }

        @Test
        @DisplayName("출발지 수정 시에도 서비스 지역 밖 좌표면 OUT_OF_SERVICE_AREA 예외가 발생한다")
        void updateLocationVote_outOfServiceArea_throwsException() {
            com.dnd.moyeolak.domain.meeting.dto.UpdateLocationVoteRequest request =
                    new com.dnd.moyeolak.domain.meeting.dto.UpdateLocationVoteRequest(
                            "홍길동",
                            "경북 울릉군 독도",
                            "37.2426",
                            "131.8597"
                    );

            assertThatThrownBy(() -> locationService.updateLocationVote(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_SERVICE_AREA);
        }
    }

    @Nested
    @DisplayName("중복 localStorageKey 예외")
    class DuplicateLocalStorageKey {

        @Test
        @DisplayName("일반 참여자가 이미 존재하는 localStorageKey로 요청 시 DUPLICATE_LOCAL_STORAGE_KEY 예외가 발생한다")
        void createLocationVote_duplicateKeyForNonHost_throwsDuplicateException() {
            // given
            String meetingId = "meeting-id-123";
            Meeting meeting = Meeting.ofId(meetingId);
            Participant existingParticipant = Participant.of(meeting, "duplicate-key", "기존참여자");
            meeting.addParticipant(existingParticipant);

            CreateLocationVoteRequest request = new CreateLocationVoteRequest(
                    meetingId,
                    "duplicate-key",
                    "새참여자",
                    "서울시 강남구",
                    "37.4979502",
                    "127.0276368"
            );

            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            // when & then
            assertThatThrownBy(() -> locationService.createLocationVote(request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_LOCAL_STORAGE_KEY);
        }
    }

    @Nested
    @DisplayName("출발지 목록 조회")
    class ListVotes {

        @Test
        @DisplayName("meetingId로 출발지를 조회한다")
        void listLocationVote_byMeetingId_returnsResponses() {
            // given
            String meetingId = "meeting-id-123";
            Meeting meeting = Meeting.ofId(meetingId);
            LocationPoll locationPoll = LocationPoll.ofId(1L);
            meeting.addPolls(null, locationPoll);

            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            LocationVote locationVote = LocationVote.of(
                    locationPoll,
                    "김철수",
                    "서울시 강남구",
                    new BigDecimal("37.4979502"),
                    new BigDecimal("127.0276368")
            );
            when(locationVoteRepository.findByLocationPoll_Id(locationPoll.getId()))
                    .thenReturn(List.of(locationVote));

            // when
            List<LocationVoteResponse> responses = locationService.listLocationVote(meetingId);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().participantName()).isEqualTo("김철수");
            assertThat(responses.getFirst().departureLocation()).isEqualTo("서울시 강남구");
        }

        @Test
        @DisplayName("meetingId가 존재하지 않으면 예외가 발생한다")
        void listLocationVote_meetingNotFound_throwsException() {
            // given
            String meetingId = "missing-id";
            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> locationService.listLocationVote(meetingId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_NOT_FOUND);
        }

        @Test
        @DisplayName("meetingId는 존재하지만 locationPoll이 없으면 예외가 발생한다")
        void listLocationVote_withoutLocationPoll_throwsException() {
            // given
            String meetingId = "meeting-without-poll";
            Meeting meeting = Meeting.ofId(meetingId);
            when(meetingRepository.findByIdWithAllAssociations(meetingId)).thenReturn(Optional.of(meeting));

            // when & then
            assertThatThrownBy(() -> locationService.listLocationVote(meetingId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_POLL_NOT_FOUND);
        }
    }
}
