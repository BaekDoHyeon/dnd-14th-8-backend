package com.dnd.moyeolak.domain.location.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "출발지 추가",
        description = """
                    장소 조율을 위해 출발지를 추가합니다.

                    ### 사용 시점
                    - 참가자가 모임 장소 투표에 참여할 때
                    - 출발지 좌표와 주소를 함께 등록

                    ### 좌표 입력
                    - 프론트에서 Kakao/Google Maps API로 좌표 획득
                    - `departureLat`, `departureLng`는 문자열로 전달
                    """)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "출발지 추가 성공",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "성공 응답",
                                value = """
                                            {
                                              "code": "S100",
                                              "message": "요청이 성공적으로 처리되었습니다."
                                            }
                                            """
                        )
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 데이터 검증 실패",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "필수값 누락",
                                summary = "모임 ID 미입력",
                                value = """
                                            {
                                              "code": "E103",
                                              "message": "유효성 검증 실패",
                                              "data": {
                                                "meetingId": "must not be blank"
                                              }
                                            }
                                            """
                        )
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 또는 참여자를 찾을 수 없음",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "모임 없음",
                                        value = """
                                                    {
                                                      "code": "E410",
                                                      "message": "모임이 존재하지 않습니다."
                                                    }
                                                    """
                                ),
                                @ExampleObject(
                                        name = "참여자 없음",
                                        summary = "participantId에 해당하는 참여자가 모임에 없음",
                                        value = """
                                                    {
                                                      "code": "E414",
                                                      "message": "참여자가 존재하지 않습니다."
                                                    }
                                                    """
                                )
                        }
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 출발지를 등록한 참여자",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "localStorageKey 중복",
                                        summary = "같은 브라우저 키로 이미 출발지를 등록함",
                                        value = """
                                                    {
                                                      "code": "E413",
                                                      "message": "이미 참여한 사용자입니다."
                                                    }
                                                    """
                                ),
                                @ExampleObject(
                                        name = "participantId 중복",
                                        summary = "지정한 참여자가 이미 출발지를 등록함",
                                        value = """
                                                    {
                                                      "code": "E430",
                                                      "message": "이미 출발지를 등록한 참여자입니다."
                                                    }
                                                    """
                                )
                        }
                )
        )
})
public @interface CreateLocationVoteApiDocs {
}
