package com.dnd.moyeolak.domain.location.docs;

import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "중간지점 추천",
        description = """
                    등록된 출발지를 기반으로 최적의 중간 만남 장소(지하철역)를 추천합니다.

                    ### 지원 범위
                    - **수도권 전용**: 서울, 경기, 인천 지역의 지하철역만 검색됩니다
                    - 지원 노선: 1~9호선, 경의중앙선, 수인분당선, 신분당선, 경춘선, 경강선, 인천1·2호선
                    - 수도권 외 지역의 출발지로 요청 시 반경 내 역이 없어 404 응답될 수 있습니다

                    ### 알고리즘
                    1. 모든 참가자의 출발지 좌표로 **무게중심(centroid)** 계산 (PostGIS)
                    2. 무게중심 반경 5km 내 **지하철역 최대 10개** 탐색
                    3. Google Routes API 매트릭스 호출로 후보역별 **대중교통 이동시간** 일괄 계산
                    4. 평균 대중교통 이동시간 기준 **상위 3개역** 선정
                    5. 상위 3개역에 한해 Kakao Directions API로 **자동차 이동시간** 보강

                    ### 테스트용 더미 데이터
                    | meetingId | 시나리오 | 출발지 |
                    |-----------|---------|--------|
                    | `test-meeting-001` | 수도권 광역 | 수원, 강남, 일산, 인천 (무게중심: 홍대~신촌) |
                    | `test-meeting-002` | 서울 시내 | 강남, 홍대, 잠실, 노원 (무게중심: 종로~을지로) |
                    | `test-meeting-003` | 경기도 내 | 수원, 성남, 용인, 안양 (무게중심: 과천~의왕) |
                    | `test-meeting-004` | 출발지 1개 (에러) | 강남 1명만 등록 → E425 응답 |

                    ### 파라미터
                    - `meetingId` (필수): 모임 ID
                    - `departureTime` (선택): 출발 시간 (ISO 8601 형식, 예: `2026-02-18T10:30:00`). 미입력 시 현재 시각 기준으로 대중교통 소요시간을 계산합니다.

                    ### 주의사항
                    - 출발지가 2개 이상 등록되어야 합니다
                    - 반경 5km 내 지하철역이 없으면 404 응답
                    - Google Routes 대중교통 경로가 모두 실패하면 500 응답
                    - `departureTime`은 응답에 보존되며, 현재 추천 계산은 실시간 Google Routes/Kakao Directions 호출 기준으로 처리됩니다.
                    """)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "중간지점 추천 성공",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = MidpointRecommendationResponse.class),
                        examples = @ExampleObject(
                                name = "성공 응답",
                                summary = "3개 역 추천 결과",
                                value = """
                                            {
                                              "code": "S100",
                                              "message": "요청이 성공적으로 처리되었습니다.",
                                              "data": {
                                                "centerPoint": {
                                                  "latitude": 37.5665,
                                                  "longitude": 126.9780
                                                },
                                                "departureTime": "2026-02-18T10:30:00",
                                                "registeredCount": 4,
                                                "totalCount": 10,
                                                "recommendations": [
                                                  {
                                                    "rank": 1,
                                                    "stationId": 901,
                                                    "stationName": "디지털미디어시티역",
                                                    "line": "경의중앙선",
                                                    "latitude": 37.5779,
                                                    "longitude": 126.8997,
                                                    "distanceFromCenter": 1200,
                                                    "avgTransitDuration": 42.5,
                                                    "routes": [
                                                      {
                                                        "participantId": 11,
                                                        "departureName": "김철수",
                                                        "departureAddress": "수원시 장안구 영화동",
                                                        "transitDuration": 55,
                                                        "transitDistance": 21400,
                                                        "drivingDuration": 35,
                                                        "drivingDistance": 18200
                                                      },
                                                      {
                                                        "participantId": 12,
                                                        "departureName": "이영희",
                                                        "departureAddress": "서울시 강남구 역삼동",
                                                        "transitDuration": 30,
                                                        "transitDistance": 12000,
                                                        "drivingDuration": 20,
                                                        "drivingDistance": 9500
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "rank": 2,
                                                    "stationId": 902,
                                                    "stationName": "홍대입구역",
                                                    "line": "2호선",
                                                    "latitude": 37.5571,
                                                    "longitude": 126.9246,
                                                    "distanceFromCenter": 800,
                                                    "avgTransitDuration": 45.0,
                                                    "routes": [
                                                      {
                                                        "participantId": 11,
                                                        "departureName": "김철수",
                                                        "departureAddress": "수원시 장안구 영화동",
                                                        "transitDuration": 50,
                                                        "transitDistance": 20000,
                                                        "drivingDuration": 32,
                                                        "drivingDistance": 17000
                                                      },
                                                      {
                                                        "participantId": 12,
                                                        "departureName": "이영희",
                                                        "departureAddress": "서울시 강남구 역삼동",
                                                        "transitDuration": 40,
                                                        "transitDistance": 15000,
                                                        "drivingDuration": 25,
                                                        "drivingDistance": 11000
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "rank": 3,
                                                    "stationId": 903,
                                                    "stationName": "합정역",
                                                    "line": "2호선",
                                                    "latitude": 37.5495,
                                                    "longitude": 126.9139,
                                                    "distanceFromCenter": 1500,
                                                    "avgTransitDuration": 48.0,
                                                    "routes": [
                                                      {
                                                        "participantId": 11,
                                                        "departureName": "김철수",
                                                        "departureAddress": "수원시 장안구 영화동",
                                                        "transitDuration": 52,
                                                        "transitDistance": 20500,
                                                        "drivingDuration": 33,
                                                        "drivingDistance": 17500
                                                      },
                                                      {
                                                        "participantId": 12,
                                                        "departureName": "이영희",
                                                        "departureAddress": "서울시 강남구 역삼동",
                                                        "transitDuration": 44,
                                                        "transitDistance": 16000,
                                                        "drivingDuration": 28,
                                                        "drivingDistance": 12500
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                        )
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "출발지 2개 미만",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "출발지 부족",
                                summary = "출발지가 2개 미만인 경우 (0개 또는 1개)",
                                value = """
                                            {
                                              "code": "E425",
                                              "message": "출발지 2개 이상 등록 시 중간지점을 확인할 수 있습니다.",
                                              "data": {
                                                "registeredCount": 1,
                                                "totalCount": 10
                                              }
                                            }
                                            """
                        )
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 없음 또는 반경 내 지하철역 없음",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "모임 없음",
                                        summary = "모임이 존재하지 않는 경우",
                                        value = """
                                                    {
                                                      "code": "E410",
                                                      "message": "모임이 존재하지 않습니다."
                                                    }
                                                    """
                                ),
                                @ExampleObject(
                                        name = "역 없음",
                                        summary = "무게중심 반경 5km 내 지하철역이 없는 경우",
                                        value = """
                                                    {
                                                      "code": "E417",
                                                      "message": "반경 내 지하철역을 찾을 수 없습니다."
                                                    }
                                                    """
                                )
                        }
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "외부 API 호출 실패",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "API 오류",
                                summary = "Google Routes 대중교통 경로 조회 실패",
                                value = """
                                            {
                                              "code": "E418",
                                              "message": "외부 API 호출에 실패했습니다."
                                            }
                                            """
                        )
                )
        )
})
public @interface GetMidpointRecommendationsApiDocs {
}
