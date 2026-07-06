package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.DrivingRouteResult;
import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;
import com.dnd.moyeolak.domain.location.dto.ParticipantCountDto;
import com.dnd.moyeolak.domain.location.dto.StationRecommendationDto;
import com.dnd.moyeolak.domain.location.dto.TransitRouteResult;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.impl.MidpointRecommendationServiceImpl;
import com.dnd.moyeolak.domain.location.service.impl.OdsayTransitRouteClient;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.global.client.mapglot.MapGlotRouteClient;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import com.dnd.moyeolak.global.station.entity.Station;
import com.dnd.moyeolak.global.station.repository.StationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MidpointRecommendationServiceImplTest {

    @Mock
    private MeetingService meetingService;

    @Mock
    private LocationVoteRepository locationVoteRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private MapGlotRouteClient mapGlotRouteClient;

    @Mock
    private OdsayTransitRouteClient odsayTransitRouteClient;

    @InjectMocks
    private MidpointRecommendationServiceImpl midpointRecommendationService;

    private static final String MEETING_ID = "meeting-123";

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("LocationPoll이 null이면 LOCATION_POLL_NOT_FOUND 예외가 발생한다")
        void throwsWhenLocationPollIsNull() {
            Meeting meeting = mock(Meeting.class);
            when(meetingService.get(MEETING_ID)).thenReturn(meeting);
            when(meeting.getLocationPoll()).thenReturn(null);

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_POLL_NOT_FOUND);
        }

        @Test
        @DisplayName("출발지 투표가 0개이면 INSUFFICIENT_LOCATION_VOTES 예외가 발생하며 data에 참여자 수가 포함된다")
        void throwsWhenNoLocationVotes() {
            Meeting meeting = mock(Meeting.class);
            LocationPoll locationPoll = mock(LocationPoll.class);
            when(meetingService.get(MEETING_ID)).thenReturn(meeting);
            when(meeting.getLocationPoll()).thenReturn(locationPoll);
            when(meeting.getParticipantCount()).thenReturn(5);
            when(locationPoll.getId()).thenReturn(1L);
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_LOCATION_VOTES)
                    .satisfies(ex -> {
                        ParticipantCountDto data = (ParticipantCountDto) ((BusinessException) ex).getData();
                        assertThat(data.registeredCount()).isEqualTo(0);
                        assertThat(data.totalCount()).isEqualTo(5);
                    });
        }

        @Test
        @DisplayName("근처 지하철역이 없으면 NO_NEARBY_STATIONS 예외가 발생한다")
        void throwsWhenNoNearbyStations() {
            Meeting meeting = mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "테스터", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "테스터2", "서울시 송파구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            lenient().when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_NEARBY_STATIONS);
        }

        @Test
        @DisplayName("모든 후보역의 ODsay 경로가 실패하면 ODSAY_API_ERROR 예외가 발생한다")
        void throwsWhenAllOdsayRoutesFail() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(1L, "강남역", "2호선", 37.4979, 127.0276);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            when(mapGlotRouteClient.calculateDriving(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new DrivingRouteResult(900, 8000, true));
            when(odsayTransitRouteClient.calculate(any(LocationVote.class), any(Station.class)))
                    .thenReturn(TransitRouteResult.unreachable());

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ODSAY_API_ERROR);
        }
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessCases {

        @Test
        @DisplayName("MapGlot driving 정보를 응답에 반영하되 최종 순위는 ODsay transit 기준으로 결정한다")
        void ranksByOdsayTransitAndKeepsMapGlotDrivingDetails() {
            Meeting meeting = mockMeetingWithLocationPoll();
            when(meeting.getParticipantCount()).thenReturn(4);
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5500", "126.9500", "참가자B", "서울시 마포구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));

            Station station1 = createMockStation(1L, "합정역", "2호선", 37.5495, 126.9137);
            Station station2 = createMockStation(2L, "강남역", "2호선", 37.4979, 127.0276);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station1, station2));

            when(mapGlotRouteClient.calculateDriving(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new DrivingRouteResult(1800, 12000, true))
                    .thenReturn(new DrivingRouteResult(1800, 12000, true))
                    .thenReturn(new DrivingRouteResult(600, 4000, true))
                    .thenReturn(new DrivingRouteResult(600, 4000, true));
            when(odsayTransitRouteClient.calculate(vote1, station1)).thenReturn(new TransitRouteResult(20, 9000, true));
            when(odsayTransitRouteClient.calculate(vote2, station1)).thenReturn(new TransitRouteResult(30, 10000, true));
            when(odsayTransitRouteClient.calculate(vote1, station2)).thenReturn(new TransitRouteResult(70, 14000, true));
            when(odsayTransitRouteClient.calculate(vote2, station2)).thenReturn(new TransitRouteResult(80, 15000, true));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null);

            assertThat(response.centerPoint().latitude()).isEqualTo(37.525);
            assertThat(response.centerPoint().longitude()).isEqualTo(126.975);
            assertThat(response.registeredCount()).isEqualTo(2);
            assertThat(response.totalCount()).isEqualTo(4);
            assertThat(response.recommendations()).hasSize(2);

            StationRecommendationDto first = response.recommendations().getFirst();
            assertThat(first.rank()).isEqualTo(1);
            assertThat(first.stationName()).isEqualTo("합정역");
            assertThat(first.avgTransitDuration()).isEqualTo(25.0);
            assertThat(first.routes()).extracting("transitDuration").containsExactly(20, 30);
            assertThat(first.routes()).extracting("drivingDuration").containsExactly(30, 30);
            assertThat(first.routes()).extracting("drivingDistance").containsExactly(12000, 12000);
        }

        @Test
        @DisplayName("departureName이 없으면 participant 이름으로 대체한다")
        void usesParticipantNameWhenDepartureNameIsNull() {
            mockMeetingWithLocationPoll();
            LocationVote vote = mock(LocationVote.class);
            when(vote.getDepartureLat()).thenReturn(new BigDecimal("37.5000"));
            when(vote.getDepartureLng()).thenReturn(new BigDecimal("127.0000"));
            when(vote.getDepartureName()).thenReturn(null);
            when(vote.getDepartureLocation()).thenReturn("서울시 강남구");
            Participant participant = mock(Participant.class);
            when(participant.getName()).thenReturn("김참가자");
            when(vote.getParticipant()).thenReturn(participant);
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 마포구");
            when(locationVoteRepository.findByLocationPoll_Id(anyLong())).thenReturn(List.of(vote, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(6L, "역삼역", "2호선", 37.5006, 127.0366);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            when(mapGlotRouteClient.calculateDriving(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new DrivingRouteResult(600, 5000, true));
            when(odsayTransitRouteClient.calculate(any(LocationVote.class), any(Station.class)))
                    .thenReturn(new TransitRouteResult(10, 5000, true));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null);

            assertThat(response.recommendations().getFirst().routes().getFirst().departureName()).isEqualTo("김참가자");
        }

        @Test
        @DisplayName("MapGlot 기준 하위 후보역은 ODsay 정밀 평가 대상에서 제외한다")
        void filtersWorstDrivingCandidateBeforeOdsayEvaluation() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "역1", "1호선", 37.50, 127.01),
                    createMockStation(2L, "역2", "2호선", 37.51, 127.02),
                    createMockStation(3L, "역3", "3호선", 37.52, 127.03),
                    createMockStation(4L, "역4", "4호선", 37.53, 127.04),
                    createMockStation(5L, "역5", "5호선", 37.54, 127.05),
                    createMockStation(6L, "역6", "6호선", 37.55, 127.06)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(mapGlotRouteClient.calculateDriving(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new DrivingRouteResult(600, 5000, true))
                    .thenReturn(new DrivingRouteResult(600, 5000, true))
                    .thenReturn(new DrivingRouteResult(700, 6000, true))
                    .thenReturn(new DrivingRouteResult(700, 6000, true))
                    .thenReturn(new DrivingRouteResult(800, 7000, true))
                    .thenReturn(new DrivingRouteResult(800, 7000, true))
                    .thenReturn(new DrivingRouteResult(900, 8000, true))
                    .thenReturn(new DrivingRouteResult(900, 8000, true))
                    .thenReturn(new DrivingRouteResult(1000, 9000, true))
                    .thenReturn(new DrivingRouteResult(1000, 9000, true))
                    .thenReturn(new DrivingRouteResult(5000, 20000, true))
                    .thenReturn(new DrivingRouteResult(5000, 20000, true));
            when(odsayTransitRouteClient.calculate(any(LocationVote.class), any(Station.class)))
                    .thenReturn(new TransitRouteResult(10, 5000, true));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null);

            assertThat(response.recommendations()).hasSize(3);
            verify(odsayTransitRouteClient, never()).calculate(vote1, stations.get(5));
            verify(odsayTransitRouteClient, never()).calculate(vote2, stations.get(5));
        }
    }

    private Meeting mockMeetingWithLocationPoll() {
        Meeting meeting = mock(Meeting.class);
        LocationPoll locationPoll = mock(LocationPoll.class);
        when(meetingService.get(MEETING_ID)).thenReturn(meeting);
        when(meeting.getLocationPoll()).thenReturn(locationPoll);
        lenient().when(meeting.getParticipantCount()).thenReturn(5);
        when(locationPoll.getId()).thenReturn(1L);
        return meeting;
    }

    private LocationVote createMockVote(String lat, String lng, String name, String address) {
        LocationVote vote = mock(LocationVote.class);
        lenient().when(vote.getDepartureLat()).thenReturn(new BigDecimal(lat));
        lenient().when(vote.getDepartureLng()).thenReturn(new BigDecimal(lng));
        lenient().when(vote.getDepartureName()).thenReturn(name);
        lenient().when(vote.getDepartureLocation()).thenReturn(address);
        return vote;
    }

    private Station createMockStation(Long id, String name, String line, double lat, double lng) {
        Station station = mock(Station.class);
        lenient().when(station.getId()).thenReturn(id);
        lenient().when(station.getName()).thenReturn(name);
        lenient().when(station.getLine()).thenReturn(line);
        lenient().when(station.getLatitude()).thenReturn(lat);
        lenient().when(station.getLongitude()).thenReturn(lng);
        return station;
    }
}
