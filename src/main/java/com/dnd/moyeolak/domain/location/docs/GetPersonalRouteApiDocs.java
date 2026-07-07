package com.dnd.moyeolak.domain.location.docs;

import com.dnd.moyeolak.domain.location.dto.PersonalRouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "중간지점 개인 경로 상세",
        description = """
                추천된 특정 지하철역까지 한 참여자가 이동할 때의 대중교통/자동차 상세 정보를 제공합니다.

                - 추천 리스트의 참여자 아이콘을 클릭하면 이 API를 호출합니다.
                - `mode` 파라미터로 `transit`, `driving`, `both`(기본) 가운데 선택할 수 있습니다.
                - 예시 요청: `GET /api/locations/midpoint-routes?meetingId=test-meeting-001&stationId=233&participantId=1&mode=both`
                """
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "상세 경로 조회 성공",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = PersonalRouteResponse.class),
                        examples = @ExampleObject(
                                name = "성공 응답",
                                description = "meetingId=test-meeting-001, stationId=233(김포공항역 5호선), participantId=1(김민준)",
                                value = """
                                        {
                                          "code": "S100",
                                          "message": "요청이 성공적으로 처리되었습니다.",
                                          "data": {
                                            "participant": {
                                              "participantId": 1,
                                              "participantName": "김민준",
                                              "departureAddress": "수원시 영통구 영통동"
                                            },
                                            "station": {
                                              "stationId": 233,
                                              "stationName": "김포공항역",
                                              "line": "5호선"
                                            },
                                            "departureTime": "2026-02-18T10:30:00",
                                            "transit": {
                                              "durationMinutes": 92,
                                              "distanceMeters": 21400,
                                              "fare": 5800,
                                              "transferCount": 3,
                                              "walkDistanceMeters": 820
                                            },
                                            "driving": {
                                              "durationMinutes": 58,
                                              "distanceMeters": 12500,
                                              "tollFare": 5000,
                                              "estimatedTaxiFare": 55800
                                            }
                                          }
                                        }
                                        """
                        )
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "모임/참여자/출발지/역 미존재",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "출발지 없음",
                                value = """
                                        {
                                          "code": "E415",
                                          "message": "위치 응답이 존재하지 않습니다."
                                        }
                                        """
                        )
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "외부 API 오류",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "ODsay 실패",
                                value = """
                                        {
                                          "code": "E423",
                                          "message": "대중교통 경로 조회에 실패했습니다."
                                        }
                                        """
                        )
                )
        )
})
public @interface GetPersonalRouteApiDocs {
    @Parameter(
            name = "meetingId",
            description = "모임 ID",
            required = true,
            schema = @Schema(type = "string", defaultValue = "test-meeting-001", example = "test-meeting-001")
    )
    String meetingId() default "";

    @Parameter(
            name = "stationId",
            description = "추천 역 ID (예: recommendations[0].stationId). 초기 더미 데이터에서는 김포공항역(5호선)의 ID가 233입니다.",
            required = true,
            schema = @Schema(type = "integer", defaultValue = "233", example = "233")
    )
    String stationId() default "";

    @Parameter(
            name = "participantId",
            description = "참여자 ID (예: 김민준=1, 이서연=2 …)",
            required = true,
            schema = @Schema(type = "integer", defaultValue = "1", example = "1")
    )
    String participantId() default "";

    @Parameter(
            name = "departureTime",
            description = "출발 시각(ISO8601). 비워두면 현재 시각 기준.",
            schema = @Schema(type = "string", format = "date-time", defaultValue = "2026-02-18T10:30:00", example = "2026-02-18T10:30:00")
    )
    String departureTime() default "";

    @Parameter(
            name = "mode",
            description = "`transit` / `driving` / `both`",
            schema = @Schema(type = "string", defaultValue = "both", example = "both")
    )
    String mode() default "";
}
