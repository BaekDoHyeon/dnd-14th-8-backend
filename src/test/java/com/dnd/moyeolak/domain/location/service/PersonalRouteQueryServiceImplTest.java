package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.PersonalRouteResponse;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.enums.RouteMode;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.impl.PersonalRouteQueryServiceImpl;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.service.ParticipantService;
import com.dnd.moyeolak.global.client.kakao.KakaoDirectionsClient;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
import com.dnd.moyeolak.global.client.odsay.OdsayClient;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import com.dnd.moyeolak.global.station.entity.Station;
import com.dnd.moyeolak.global.station.repository.StationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalRouteQueryServiceImplTest {

    @Mock
    private MeetingService meetingService;
    @Mock
    private ParticipantService participantService;
    @Mock
    private LocationVoteRepository locationVoteRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private OdsayClient odsayClient;
    @Mock
    private KakaoDirectionsClient kakaoDirectionsClient;

    @InjectMocks
    private PersonalRouteQueryServiceImpl personalRouteQueryService;

    private static final String MEETING_ID = "meeting-123";
    private static final Long PARTICIPANT_ID = 10L;
    private static final Long STATION_ID = 50L;

    @Test
    @DisplayName("대중교통/자동차 상세 정보를 모두 반환한다")
    void returnsBothTransitAndDrivingDetails() {
        Meeting meeting = Meeting.ofId(MEETING_ID);
        Participant participant = Participant.of(Meeting.ofId(MEETING_ID), "key", "김혜인");
        setParticipantId(participant, PARTICIPANT_ID);

        LocationVote vote = mock(LocationVote.class);
        when(vote.getDepartureLat()).thenReturn(new BigDecimal("37.55"));
        when(vote.getDepartureLng()).thenReturn(new BigDecimal("126.97"));
        when(vote.getDepartureLocation()).thenReturn("서울 강서구 ...");

        Station station = mock(Station.class);
        when(station.getId()).thenReturn(STATION_ID);
        when(station.getName()).thenReturn("김포공항역");
        when(station.getLine()).thenReturn("5호선");
        when(station.getLatitude()).thenReturn(37.56);
        when(station.getLongitude()).thenReturn(126.80);

        when(meetingService.get(MEETING_ID)).thenReturn(meeting);
        when(participantService.getById(PARTICIPANT_ID)).thenReturn(participant);
        when(locationVoteRepository.findFirstByParticipant_IdOrderByCreatedAtDesc(PARTICIPANT_ID))
                .thenReturn(Optional.of(vote));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station));

        OdsayPathInfo pathInfo = new OdsayPathInfo(60, 1500, 1, 1, 10000, 400, 5);
        when(odsayClient.searchRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(pathInfo);

        KakaoDirectionsResponse.Fare fare = new KakaoDirectionsResponse.Fare(3000, 20000);
        KakaoDirectionsResponse.Summary summary = new KakaoDirectionsResponse.Summary(12000, 3600, fare);
        when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(summary);

        PersonalRouteResponse response = personalRouteQueryService.getPersonalRoute(
                MEETING_ID, STATION_ID, PARTICIPANT_ID, LocalDateTime.of(2026, 2, 18, 10, 30), RouteMode.BOTH
        );

        assertThat(response.participant().participantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(response.station().stationId()).isEqualTo(STATION_ID);
        assertThat(response.transit()).isNotNull();
        assertThat(response.transit().durationMinutes()).isEqualTo(60);
        assertThat(response.transit().fare()).isEqualTo(1500);
        assertThat(response.driving()).isNotNull();
        assertThat(response.driving().durationMinutes()).isEqualTo(60); // 3600 / 60
        assertThat(response.driving().tollFare()).isEqualTo(3000);
        assertThat(response.driving().estimatedTaxiFare()).isEqualTo(20000);
    }

    @Test
    @DisplayName("대중교통 조회가 실패하면 ODSAY_API_ERROR를 발생시킨다")
    void throwsWhenTransitFails() {
        setupCommonMocks();
        when(odsayClient.searchRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OdsayPathInfo(999, 0, 0, 0, 0, 0, 0));

        assertThatThrownBy(() -> personalRouteQueryService.getPersonalRoute(
                MEETING_ID, STATION_ID, PARTICIPANT_ID, null, RouteMode.TRANSIT
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ODSAY_API_ERROR);
    }

    @Test
    @DisplayName("자동차 조회가 실패하면 KAKAO_API_ERROR를 발생시킨다")
    void throwsWhenDrivingFails() {
        setupCommonMocks();
        when(odsayClient.searchRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OdsayPathInfo(30, 1000, 0, 0, 5000, 100, 0));
        when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> personalRouteQueryService.getPersonalRoute(
                MEETING_ID, STATION_ID, PARTICIPANT_ID, null, RouteMode.DRIVING
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.KAKAO_API_ERROR);
    }

    @Test
    @DisplayName("참여자의 출발지가 없으면 LOCATION_VOTE_NOT_FOUND 예외가 발생한다")
    void throwsWhenLocationVoteMissing() {
        Meeting meeting = Meeting.ofId(MEETING_ID);
        Participant participant = Participant.of(Meeting.ofId(MEETING_ID), "key", "김혜인");
        setParticipantId(participant, PARTICIPANT_ID);

        when(meetingService.get(MEETING_ID)).thenReturn(meeting);
        when(participantService.getById(PARTICIPANT_ID)).thenReturn(participant);
        when(locationVoteRepository.findFirstByParticipant_IdOrderByCreatedAtDesc(PARTICIPANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> personalRouteQueryService.getPersonalRoute(
                MEETING_ID, STATION_ID, PARTICIPANT_ID, null, RouteMode.BOTH
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_VOTE_NOT_FOUND);
    }

    private void setupCommonMocks() {
        Meeting meeting = Meeting.ofId(MEETING_ID);
        Participant participant = Participant.of(Meeting.ofId(MEETING_ID), "key", "김혜인");
        setParticipantId(participant, PARTICIPANT_ID);

        LocationVote vote = mock(LocationVote.class);
        when(vote.getDepartureLat()).thenReturn(new BigDecimal("37.50"));
        when(vote.getDepartureLng()).thenReturn(new BigDecimal("126.90"));
        lenient().when(vote.getDepartureLocation()).thenReturn("서울 강서구 ...");

        Station station = mock(Station.class);
        when(station.getId()).thenReturn(STATION_ID);
        lenient().when(station.getName()).thenReturn("김포공항역");
        lenient().when(station.getLine()).thenReturn("5호선");
        when(station.getLatitude()).thenReturn(37.56);
        when(station.getLongitude()).thenReturn(126.80);

        when(meetingService.get(MEETING_ID)).thenReturn(meeting);
        when(participantService.getById(PARTICIPANT_ID)).thenReturn(participant);
        when(locationVoteRepository.findFirstByParticipant_IdOrderByCreatedAtDesc(PARTICIPANT_ID))
                .thenReturn(Optional.of(vote));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(station));
    }

    private void setParticipantId(Participant participant, Long id) {
        try {
            var field = Participant.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(participant, id);
        } catch (Exception ignored) {
        }
    }
}
