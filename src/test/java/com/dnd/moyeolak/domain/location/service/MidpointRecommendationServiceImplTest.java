package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;
import com.dnd.moyeolak.domain.location.dto.ParticipantCountDto;
import com.dnd.moyeolak.domain.location.dto.StationRecommendationDto;
import com.dnd.moyeolak.domain.location.dto.TransitRouteResult;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.impl.MidpointRecommendationServiceImpl;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.global.client.google.GoogleRoutesClient;
import com.dnd.moyeolak.global.client.kakao.KakaoDirectionsClient;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.ratelimit.MidpointRecommendationUsageLimiter;
import com.dnd.moyeolak.global.response.ErrorCode;
import com.dnd.moyeolak.global.station.entity.Station;
import com.dnd.moyeolak.global.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class MidpointRecommendationServiceImplTest {

    @Mock
    private MeetingService meetingService;

    @Mock
    private LocationVoteRepository locationVoteRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private GoogleRoutesClient googleRoutesClient;

    @Mock
    private KakaoDirectionsClient kakaoDirectionsClient;

    @Mock
    private MidpointRecommendationUsageLimiter usageLimiter;

    @Mock
    private TransactionTemplate readOnlyTransactionTemplate;

    @InjectMocks
    private MidpointRecommendationServiceImpl midpointRecommendationService;

    private static final String MEETING_ID = "meeting-123";

    @BeforeEach
    void setUpTransactionTemplate() {
        // 실제 트랜잭션 없이 콜백을 즉시 실행 — DB 조회 구간만 트랜잭션으로 감싸는 구조를 단위 테스트에서 재현한다
        lenient().when(readOnlyTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("LocationPoll이 null이면 LOCATION_POLL_NOT_FOUND 예외가 발생한다")
        void throwsWhenLocationPollIsNull() {
            Meeting meeting = mock(Meeting.class);
            when(meetingService.get(MEETING_ID)).thenReturn(meeting);
            when(meeting.getLocationPoll()).thenReturn(null);

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown"))
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

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown"))
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
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "테스터", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "테스터2", "서울시 송파구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            lenient().when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_NEARBY_STATIONS);
        }

        @Test
        @DisplayName("모든 후보역이 전원 도달 불가이면 NO_REACHABLE_STATIONS 예외가 발생한다")
        void throwsWhenAllTransitRoutesUnreachable() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(1L, "강남역", "2호선", 37.4979, 127.0276);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(TransitRouteResult.unreachable()),
                            List.of(TransitRouteResult.unreachable())
                    ));

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_REACHABLE_STATIONS);
        }

        @Test
        @DisplayName("재계산 제한을 초과하면 외부 API를 호출하지 않고 429 예외가 발생한다")
        void throwsRateLimitExceededBeforeCallingExternalApis() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(1L, "강남역", "2호선", 37.4979, 127.0276);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            doThrow(new BusinessException(ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED))
                    .when(usageLimiter).checkAndIncrease(eq(MEETING_ID), eq("203.0.113.10"));

            assertThatThrownBy(() -> midpointRecommendationService.calculateMidpointRecommendations(
                    MEETING_ID, null, "203.0.113.10"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode",
                            ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED
                    );

            verify(googleRoutesClient, never()).computeTransitMatrix(anyList(), anyList(), any());
            verify(kakaoDirectionsClient, never())
                    .requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        }
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessCases {

        @Test
        @DisplayName("Google 매트릭스 기준으로 순위를 매기고 Kakao 자동차 경로를 응답에 반영한다")
        void ranksByGoogleTransitAndEnrichesWithKakaoDriving() {
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

            // matrix[voteIdx][stationIdx]: 합정역 평균 25분 < 강남역 평균 75분
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(20, 9000, true), new TransitRouteResult(70, 14000, true)),
                            List.of(new TransitRouteResult(30, 10000, true), new TransitRouteResult(80, 15000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(12000, 1800, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

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
        @DisplayName("Top 3를 초과하는 후보역은 제외하고 Kakao 호출도 Top 3에만 수행한다")
        void limitsRecommendationsToTopThreeAndEnrichesOnlyThose() {
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
                    createMockStation(5L, "역5", "5호선", 37.54, 127.05)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            // 역1(10분)이 1위, 역5(50분)가 꼴찌
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(10, 1000, true), new TransitRouteResult(20, 2000, true),
                                    new TransitRouteResult(30, 3000, true), new TransitRouteResult(40, 4000, true),
                                    new TransitRouteResult(50, 5000, true)),
                            List.of(new TransitRouteResult(10, 1000, true), new TransitRouteResult(20, 2000, true),
                                    new TransitRouteResult(30, 3000, true), new TransitRouteResult(40, 4000, true),
                                    new TransitRouteResult(50, 5000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(5000, 600, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations()).hasSize(3);
            assertThat(response.recommendations()).extracting("stationName")
                    .containsExactly("역1", "역2", "역3");
            assertThat(response.recommendations()).extracting("rank").containsExactly(1, 2, 3);
            // Kakao 자동차 보강은 Top 3 x 참가자 2명 = 6회만 호출
            verify(kakaoDirectionsClient, times(6))
                    .requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
        }

        @Test
        @DisplayName("Kakao 자동차 경로가 실패해도 추천은 성공하고 해당 경로만 도달 불가 값이 된다")
        void succeedsWhenKakaoDrivingFails() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(6L, "역삼역", "2호선", 37.5006, 127.0366);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(10, 5000, true)),
                            List.of(new TransitRouteResult(15, 6000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(null);

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations()).hasSize(1);
            assertThat(response.recommendations().getFirst().routes())
                    .extracting("drivingDuration").containsExactly(999, 999);
        }

        @Test
        @DisplayName("도달 불가 참가자가 있는 역보다 전원 도달 가능한 역이 우선 추천된다")
        void prefersFullyReachableStationOverPartiallyReachableOne() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.5100", "127.0100", "참가자B", "서울시 강동구");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station partiallyReachable = createMockStation(1L, "역1", "1호선", 37.50, 127.01);
            Station fullyReachable = createMockStation(2L, "역2", "2호선", 37.51, 127.02);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(partiallyReachable, fullyReachable));
            // 역1: 참가자A만 도달 가능(10분, 평균 10분) / 역2: 전원 도달 가능(평균 50분)
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(10, 1000, true), new TransitRouteResult(50, 5000, true)),
                            List.of(TransitRouteResult.unreachable(), new TransitRouteResult(50, 5000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(5000, 600, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations()).extracting("stationName")
                    .containsExactly("역2", "역1");
        }

        @Test
        @DisplayName("도달 불가 경로는 RouteDto의 reachable 플래그가 false로 내려간다")
        void marksUnreachableRoutesInRouteDto() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.2426", "131.8597", "참가자B", "경북 울릉군 독도");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            Station station = createMockStation(6L, "역삼역", "2호선", 37.5006, 127.0366);
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(List.of(station));
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(10, 5000, true)),
                            List.of(TransitRouteResult.unreachable())
                    ));
            // 참가자A는 자동차 경로 성공, 참가자B(독도)는 실패(null)
            when(kakaoDirectionsClient.requestDrivingRoute(eq(37.5000), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(5000, 600, null));
            when(kakaoDirectionsClient.requestDrivingRoute(eq(37.2426), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(null);

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations().getFirst().routes())
                    .extracting("transitReachable").containsExactly(true, false);
            assertThat(response.recommendations().getFirst().routes())
                    .extracting("drivingReachable").containsExactly(true, false);
        }

        @Test
        @DisplayName("추천 후보 간 평균 시간이 비슷하고 모두 15분 이내이면 근거리 출발지 결과 타입을 반환한다")
        void returnsNearbyDeparturesResultTypeForShortSimilarRoutes() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5550", "126.9100", "참가자A", "합정역");
            LocationVote vote2 = createMockVote("37.5570", "126.9240", "참가자B", "홍대입구역");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "합정역", "2호선", 37.5495, 126.9137),
                    createMockStation(2L, "홍대입구역", "2호선", 37.5572, 126.9245),
                    createMockStation(3L, "상수역", "6호선", 37.5477, 126.9229)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(8, 1000, true), new TransitRouteResult(10, 1300, true),
                                    new TransitRouteResult(11, 1400, true)),
                            List.of(new TransitRouteResult(9, 1100, true), new TransitRouteResult(8, 900, true),
                                    new TransitRouteResult(12, 1500, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(2500, 480, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.resultType().name()).isEqualTo("NEARBY_DEPARTURES");
        }

        @Test
        @DisplayName("출발지가 가까우면 추천 후보 간 평균 시간이 벌어져도 근거리 출발지 결과 타입을 반환한다")
        void returnsNearbyDeparturesResultTypeWhenDeparturesAreCloseEvenIfRecommendationDurationsDiffer() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5727", "127.0164", "참가자A", "동묘앞역");
            LocationVote vote2 = createMockVote("37.5714", "127.0095", "참가자B", "동대문역");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "동묘앞역", "1호선", 37.5732, 127.0165),
                    createMockStation(2L, "동대문역", "1호선", 37.5714, 127.0095),
                    createMockStation(3L, "종로5가역", "1호선", 37.5709, 127.0019)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(6, 500, true), new TransitRouteResult(8, 800, true),
                                    new TransitRouteResult(18, 1600, true)),
                            List.of(new TransitRouteResult(7, 600, true), new TransitRouteResult(5, 400, true),
                                    new TransitRouteResult(20, 1800, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(1500, 360, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.resultType().name()).isEqualTo("NEARBY_DEPARTURES");
        }

        @Test
        @DisplayName("출발지가 같은 역 근처에 모여 있으면 중심점에 가장 가까운 역을 우선 추천한다")
        void prefersNearestStationToCenterWhenDeparturesAreClose() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5572", "126.9245", "참가자A", "홍대입구역");
            LocationVote vote2 = createMockVote("37.5573", "126.9246", "참가자B", "홍대입구역");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "신촌역", "2호선", 37.5551, 126.9368),
                    createMockStation(2L, "합정역", "2호선", 37.5495, 126.9137),
                    createMockStation(3L, "홍대입구역", "2호선", 37.5572, 126.9245)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    new TransitRouteResult(12, 100, true)),
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    new TransitRouteResult(12, 100, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(1200, 300, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations()).extracting("stationName")
                    .containsExactly("홍대입구역", "신촌역", "합정역");
            assertThat(response.resultType().name()).isEqualTo("NEARBY_DEPARTURES");
        }

        @Test
        @DisplayName("출발지가 역 바로 근처이면 대중교통 매트릭스가 누락되어도 도보 경로로 보정한다")
        void treatsVeryCloseUnreachableTransitRoutesAsWalkable() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5572", "126.9245", "참가자A", "홍대입구역");
            LocationVote vote2 = createMockVote("37.5573", "126.9246", "참가자B", "홍대입구역");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "신촌역", "2호선", 37.5551, 126.9368),
                    createMockStation(2L, "합정역", "2호선", 37.5495, 126.9137),
                    createMockStation(3L, "홍대입구역", "2호선", 37.5572, 126.9245)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    TransitRouteResult.unreachable()),
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    TransitRouteResult.unreachable())
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(1200, 300, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            StationRecommendationDto first = response.recommendations().getFirst();
            assertThat(first.stationName()).isEqualTo("홍대입구역");
            assertThat(first.routes()).extracting("transitReachable").containsExactly(true, true);
            assertThat(first.routes()).extracting("transitDuration").containsExactly(1, 1);
        }

        @Test
        @DisplayName("출발지가 역 바로 근처이면 대중교통 시간이 과도하게 길어도 도보 경로로 보정한다")
        void shortensInflatedTransitRoutesWhenDepartureIsVeryCloseToStation() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5572", "126.9245", "참가자A", "홍대입구역 2호선");
            LocationVote vote2 = createMockVote("37.5570", "126.9269", "참가자B", "홍대입구역 공항철도");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "신촌역", "2호선", 37.5551, 126.9368),
                    createMockStation(2L, "합정역", "2호선", 37.5495, 126.9137),
                    createMockStation(3L, "홍대입구역", "2호선", 37.5572, 126.9245)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    new TransitRouteResult(18, 0, true)),
                            List.of(new TransitRouteResult(4, 900, true), new TransitRouteResult(5, 1000, true),
                                    new TransitRouteResult(22, 0, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(1200, 300, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            StationRecommendationDto first = response.recommendations().getFirst();
            assertThat(first.stationName()).isEqualTo("홍대입구역");
            assertThat(first.routes()).extracting("transitReachable").containsExactly(true, true);
            assertThat(first.routes()).extracting("transitDuration").containsExactly(1, 3);
            assertThat(response.resultType().name()).isEqualTo("NEARBY_DEPARTURES");
        }

        @Test
        @DisplayName("출발지가 멀리 떨어져 있으면 일반 중간지점 결과 타입을 반환한다")
        void returnsNormalResultTypeWhenDeparturesAreFarApart() {
            mockMeetingWithLocationPoll();
            LocationVote vote1 = createMockVote("37.5000", "127.0000", "참가자A", "서울시 강남구");
            LocationVote vote2 = createMockVote("37.6500", "126.7700", "참가자B", "경기 고양시");
            when(locationVoteRepository.findByLocationPoll_Id(1L)).thenReturn(List.of(vote1, vote2));
            when(stationRepository.calculateCentroid(any())).thenThrow(new RuntimeException("PostGIS 미지원"));
            List<Station> stations = List.of(
                    createMockStation(1L, "디지털미디어시티역", "공항철도", 37.5766, 126.9009),
                    createMockStation(2L, "홍대입구역", "2호선", 37.5572, 126.9245),
                    createMockStation(3L, "강남역", "2호선", 37.4979, 127.0276)
            );
            when(stationRepository.findNearbyStations(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(stations);
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(30, 9000, true), new TransitRouteResult(45, 12000, true),
                                    new TransitRouteResult(70, 20000, true)),
                            List.of(new TransitRouteResult(34, 10000, true), new TransitRouteResult(52, 15000, true),
                                    new TransitRouteResult(80, 24000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(12000, 1800, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.resultType().name()).isEqualTo("NORMAL");
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
            when(googleRoutesClient.computeTransitMatrix(anyList(), anyList(), any()))
                    .thenReturn(List.of(
                            List.of(new TransitRouteResult(10, 5000, true)),
                            List.of(new TransitRouteResult(15, 6000, true))
                    ));
            when(kakaoDirectionsClient.requestDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(new KakaoDirectionsResponse.Summary(5000, 600, null));

            MidpointRecommendationResponse response =
                    midpointRecommendationService.calculateMidpointRecommendations(MEETING_ID, null, "unknown");

            assertThat(response.recommendations().getFirst().routes().getFirst().departureName())
                    .isEqualTo("김참가자");
        }
    }

    @Nested
    @DisplayName("캐시 키 출발 시각 라운딩")
    class CacheKeyRounding {

        @Test
        @DisplayName("departureTime이 null이면 고정된 키를 반환한다")
        void roundDepartureTimeForCacheKey_null_returnsFixedKey() {
            assertThat(MidpointRecommendationServiceImpl.roundDepartureTimeForCacheKey(null))
                    .isEqualTo("now");
        }

        @Test
        @DisplayName("같은 10분 버킷에 속하는 서로 다른 초 단위 시각은 같은 키로 라운딩된다")
        void roundDepartureTimeForCacheKey_withinSameBucket_returnsSameKey() {
            LocalDateTime t1 = LocalDateTime.of(2026, 7, 25, 15, 1, 5);
            LocalDateTime t2 = LocalDateTime.of(2026, 7, 25, 15, 9, 59);

            assertThat(MidpointRecommendationServiceImpl.roundDepartureTimeForCacheKey(t1))
                    .isEqualTo(MidpointRecommendationServiceImpl.roundDepartureTimeForCacheKey(t2));
        }

        @Test
        @DisplayName("다른 10분 버킷에 속하는 시각은 다른 키로 라운딩된다")
        void roundDepartureTimeForCacheKey_differentBuckets_returnsDifferentKeys() {
            LocalDateTime t1 = LocalDateTime.of(2026, 7, 25, 15, 9, 59);
            LocalDateTime t2 = LocalDateTime.of(2026, 7, 25, 15, 10, 0);

            assertThat(MidpointRecommendationServiceImpl.roundDepartureTimeForCacheKey(t1))
                    .isNotEqualTo(MidpointRecommendationServiceImpl.roundDepartureTimeForCacheKey(t2));
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
