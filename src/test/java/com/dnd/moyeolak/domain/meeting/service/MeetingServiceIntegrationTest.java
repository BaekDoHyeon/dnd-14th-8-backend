package com.dnd.moyeolak.domain.meeting.service;

import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.meeting.dto.CreateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleResponse;
import com.dnd.moyeolak.domain.meeting.dto.MyMeetingResponse;
import com.dnd.moyeolak.domain.meeting.dto.UpdateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.schedule.entity.SchedulePoll;
import com.dnd.moyeolak.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeetingServiceIntegrationTest {

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("모임 생성 시 meetingId가 반환된다")
    void createMeeting_returnsMeetingId() {
        // given
        CreateMeetingRequest request = new CreateMeetingRequest(
                5,
                "local-storage-key-123",
                "홍길동"
        );

        // when
        String meetingId = meetingService.createMeeting(request);

        // then
        assertThat(meetingId).isNotNull();
        assertThat(meetingId).isNotEmpty();
    }

    @Test
    @DisplayName("모임 생성 시 참가자 수가 올바르게 저장된다")
    void createMeeting_savesParticipantCount() {
        // given
        int expectedParticipantCount = 5;
        CreateMeetingRequest request = new CreateMeetingRequest(
                expectedParticipantCount,
                "local-storage-key-123",
                "홍길동"
        );

        // when
        String meetingId = meetingService.createMeeting(request);

        // then
        Meeting saved = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(saved.getParticipantCount()).isEqualTo(expectedParticipantCount);
    }

    @Test
    @DisplayName("모임 생성 시 참가자가 추가된다")
    void createMeeting_addsParticipant() {
        // given
        String expectedName = "홍길동";
        String expectedLocalStorageKey = "local-storage-key-123";
        CreateMeetingRequest request = new CreateMeetingRequest(
                5,
                expectedLocalStorageKey,
                expectedName
        );

        // when
        String meetingId = meetingService.createMeeting(request);

        // then
        Meeting saved = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(saved.getParticipants()).hasSize(1);
        assertThat(saved.getParticipants().getFirst().getName()).isEqualTo(expectedName);
        assertThat(saved.getParticipants().getFirst().getLocalStorageKey()).isEqualTo(expectedLocalStorageKey);
    }

    @Test
    @DisplayName("모임 생성 시 일정 투표가 생성된다")
    void createMeeting_createsSchedulePoll() {
        // given
        CreateMeetingRequest request = new CreateMeetingRequest(
                5,
                "local-storage-key-123",
                "홍길동"
        );

        // when
        String meetingId = meetingService.createMeeting(request);

        // then
        Meeting saved = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(saved.getSchedulePoll()).isNotNull();
    }

    @Test
    @DisplayName("모임 생성 시 장소 투표가 생성된다")
    void createMeeting_createsLocationPoll() {
        // given
        CreateMeetingRequest request = new CreateMeetingRequest(
                5,
                "local-storage-key-123",
                "홍길동"
        );

        // when
        String meetingId = meetingService.createMeeting(request);

        // then
        Meeting saved = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(saved.getLocationPoll()).isNotNull();
    }

    @Test
    @DisplayName("호스트가 모임의 참여인원 정보를 수정한다.")
    void updateMeeting_participantCountEdit() {
        // given
        String testMeetingId = createTestMeeting(); // meeting.getParticipantCount() == 5
        UpdateMeetingRequest updateMeetingRequest = new UpdateMeetingRequest(
                testMeetingId
                , 10
                , "local-storage-key-123"
        );

        // when
        meetingService.updateMeeting(updateMeetingRequest);

        // then
        Meeting meeting = meetingService.get(testMeetingId);
        assertThat(meeting.getParticipantCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("호스트가 아닌 참여자가 모임의 참여인원 정보를 수정할 수 없다.")
    void updateMeeting_notForbiddenEdit() {
        // given
        String testMeetingId = createTestMeeting();
        UpdateMeetingRequest updateMeetingRequest = new UpdateMeetingRequest(
                testMeetingId
                , 10
                , "test1"
        );

        // when & then
        assertThatThrownBy(() -> meetingService.updateMeeting(updateMeetingRequest))
                .hasMessage("모임 수정 권한이 없습니다.");
    }

    @Test
    @DisplayName("현재 참여인원보다 더 적은 수로 인원 정보를 수정할 수 없습니다.")
    void updateMeeting_participantCountBelowCurrent() {
        // given
        String testMeetingId = createTestMeeting();
        UpdateMeetingRequest updateMeetingRequest = new UpdateMeetingRequest(
                testMeetingId
                , 4
                , "local-storage-key-123"
        );

        // when & then
        assertThatThrownBy(() -> meetingService.updateMeeting(updateMeetingRequest))
                .hasMessage("현재 참여 인원보다 적은 수로 변경할 수 없습니다.");
    }

    @Nested
    @DisplayName("모임 일정 조회")
    class GetMeetingSchedules {

        @Test
        @DisplayName("생성된 모임의 일정 정보를 조회할 수 있다")
        void getMeetingSchedules_returnsScheduleInfo() {
            // given
            CreateMeetingRequest request = new CreateMeetingRequest(
                    5,
                    "local-storage-key-123",
                    "홍길동"
            );
            String meetingId = meetingService.createMeeting(request);

            // when
            GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.meetingId()).isEqualTo(meetingId);
        }

        @Test
        @DisplayName("조회된 모임에 참가자 정보가 포함된다")
        void getMeetingSchedules_includesParticipants() {
            // given
            String participantName = "홍길동";
            CreateMeetingRequest request = new CreateMeetingRequest(
                    5,
                    "local-storage-key-123",
                    participantName
            );
            String meetingId = meetingService.createMeeting(request);

            // when
            GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

            // then
            assertThat(response.participants()).hasSize(1);
            assertThat(response.participants().get(0).name()).isEqualTo(participantName);
        }

        @Test
        @DisplayName("조회된 모임에 일정 투표 설정 정보가 포함된다")
        void getMeetingSchedules_includesSchedulePollSettings() {
            // given
            CreateMeetingRequest request = new CreateMeetingRequest(
                    5,
                    "local-storage-key-123",
                    "홍길동"
            );
            String meetingId = meetingService.createMeeting(request);

            // when
            GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

            // then
            assertThat(response.dateOptions()).isNotEmpty();
            assertThat(response.startTime()).isEqualTo("09:00");
            assertThat(response.endTime()).isEqualTo("24:00");
        }

        @Test
        @DisplayName("조회된 모임에 참가자 수와 투표한 참가자 수가 포함된다")
        void getMeetingSchedules_includesParticipantCounts() {
            // given
            CreateMeetingRequest request = new CreateMeetingRequest(
                    5,
                    "local-storage-key-123",
                    "홍길동"
            );
            String meetingId = meetingService.createMeeting(request);

            // when
            GetMeetingScheduleResponse response = meetingService.getMeetingSchedules(meetingId);

            // then
            assertThat(response.participantCount()).isEqualTo(5);
            assertThat(response.votedParticipantCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("존재하지 않는 모임 조회 시 예외가 발생한다")
        void getMeetingSchedules_throwsException_whenMeetingNotFound() {
            // given
            String nonExistentMeetingId = "non-existent-id";

            // when & then
            assertThatThrownBy(() -> meetingService.getMeetingSchedules(nonExistentMeetingId))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("모임 삭제")
    class DeleteMeeting {

        @Test
        @DisplayName("모임 삭제 시 모임이 DB에서 삭제된다")
        void deleteMeeting_removesMeetingFromDb() {
            // given
            String meetingId = createTestMeeting();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            assertThat(em.find(Meeting.class, meetingId)).isNull();
        }

        @Test
        @DisplayName("모임 삭제 시 연관된 일정 투표가 함께 삭제된다")
        void deleteMeeting_deletesSchedulePoll() {
            // given
            String meetingId = createTestMeeting();
            Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId).orElseThrow();
            Long schedulePollId = meeting.getSchedulePoll().getId();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            assertThat(em.find(SchedulePoll.class, schedulePollId)).isNull();
        }

        @Test
        @DisplayName("모임 삭제 시 연관된 장소 투표가 함께 삭제된다")
        void deleteMeeting_deletesLocationPoll() {
            // given
            String meetingId = createTestMeeting();
            Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId).orElseThrow();
            Long locationPollId = meeting.getLocationPoll().getId();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            assertThat(em.find(LocationPoll.class, locationPollId)).isNull();
        }

        @Test
        @DisplayName("모임 삭제 시 연관된 참가자가 함께 삭제된다")
        void deleteMeeting_deletesParticipants() {
            // given
            String meetingId = createTestMeeting();
            Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId).orElseThrow();
            Long participantId = meeting.getParticipants().getFirst().getId();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            assertThat(em.find(Participant.class, participantId)).isNull();
        }

        @Test
        @DisplayName("모임 삭제 시 연관된 일정 투표 데이터가 함께 삭제된다")
        void deleteMeeting_deletesScheduleVotes() {
            // given
            String meetingId = createTestMeeting();
            Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId).orElseThrow();
            Long schedulePollId = meeting.getSchedulePoll().getId();
            Long participantId = meeting.getParticipants().getFirst().getId();

            LocalDateTime now = LocalDateTime.now();
            String votedDateJson = "[\"" + now + "\"]";
            em.createNativeQuery("INSERT INTO schedule_vote (participant_id, schedule_poll_id, voted_date, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
                    .setParameter(1, participantId)
                    .setParameter(2, schedulePollId)
                    .setParameter(3, votedDateJson)
                    .setParameter(4, now)
                    .setParameter(5, now)
                    .executeUpdate();
            em.flush();
            em.clear();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            Long count = em.createQuery(
                    "SELECT COUNT(sv) FROM ScheduleVote sv WHERE sv.schedulePoll.id = :id", Long.class)
                    .setParameter("id", schedulePollId)
                    .getSingleResult();
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("모임 삭제 시 연관된 장소 투표 데이터가 함께 삭제된다")
        void deleteMeeting_deletesLocationVotes() {
            // given
            String meetingId = createTestMeeting();
            Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId).orElseThrow();
            Long locationPollId = meeting.getLocationPoll().getId();
            Long participantId = meeting.getParticipants().getFirst().getId();

            LocalDateTime now = LocalDateTime.now();
            em.createNativeQuery("INSERT INTO location_vote (location_poll_id, participant_id, departure_location, departure_lat, departure_lng, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, locationPollId)
                    .setParameter(2, participantId)
                    .setParameter(3, "강남역")
                    .setParameter(4, new BigDecimal("37.4979502"))
                    .setParameter(5, new BigDecimal("127.0276368"))
                    .setParameter(6, now)
                    .setParameter(7, now)
                    .executeUpdate();
            em.flush();
            em.clear();

            // when
            meetingService.deleteMeeting(meetingId);
            em.flush();
            em.clear();

            // then
            Long count = em.createQuery(
                    "SELECT COUNT(lv) FROM LocationVote lv WHERE lv.locationPoll.id = :id", Long.class)
                    .setParameter("id", locationPollId)
                    .getSingleResult();
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 모임 삭제 시 예외가 발생한다")
        void deleteMeeting_throwsException_whenMeetingNotFound() {
            // given
            String nonExistentMeetingId = "non-existent-id";

            // when & then
            assertThatThrownBy(() -> meetingService.deleteMeeting(nonExistentMeetingId))
                    .isInstanceOf(BusinessException.class);
        }

    }

    @Nested
    @DisplayName("내 모임 조회")
    class FindMyMeetings {

        @Test
        @DisplayName("localStorageKey가 일치하는 모임만 조회한다")
        void findMyMeetings_returnsOnlyMeetingsForLocalStorageKey() {
            // given
            String myKey = "my-local-storage-key";
            String myMeetingId = meetingService.createMeeting(new CreateMeetingRequest(
                    4,
                    myKey,
                    "민수"
            ));
            meetingService.createMeeting(new CreateMeetingRequest(
                    3,
                    "other-local-storage-key",
                    "지영"
            ));
            em.flush();
            em.clear();

            // when
            List<MyMeetingResponse> responses = meetingService.findMyMeetings(myKey);

            // then
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().meetingId()).isEqualTo(myMeetingId);
            assertThat(responses.getFirst().hostName()).isEqualTo("민수");
            assertThat(responses.getFirst().participantCount()).isEqualTo(4);
            assertThat(responses.getFirst().isHost()).isTrue();
            assertThat(responses.getFirst().createdAt()).isNotNull();
        }
    }

    private String createTestMeeting() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                5,
                "local-storage-key-123",
                "홍길동"
        );
        String meetingId = meetingService.createMeeting(request);

        Meeting meeting = em.find(Meeting.class, meetingId);  // 영속 상태 엔티티 조회
        meeting.addParticipant(Participant.of(meeting, "test1", "테스터1"));
        meeting.addParticipant(Participant.of(meeting, "test2", "테스터2"));
        meeting.addParticipant(Participant.of(meeting, "test3", "테스터3"));
        meeting.addParticipant(Participant.of(meeting, "test4", "테스터4"));
        em.flush();
        em.clear();
        return meetingId;
    }
}
