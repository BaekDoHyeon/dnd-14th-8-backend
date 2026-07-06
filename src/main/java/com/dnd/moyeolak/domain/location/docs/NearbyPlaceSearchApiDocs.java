package com.dnd.moyeolak.domain.location.docs;

import com.dnd.moyeolak.domain.location.dto.NearbyPlaceSearchResponse;
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
        summary = "주변 장소 검색",
        description = """
                    기준점(지하철역) 좌표를 기반으로 주변 장소를 카테고리별로 검색합니다.

                    ### 검색 카테고리
                    - 스터디카페, 회의실, 카페, 음식점

                    ### 동작 방식
                    1. DB 캐시 조회 (30일 TTL)
                    2. 캐시 미스 시 Kakao Local 키워드 검색으로 실시간 검색
                    3. 검증된 장소만 DB에 캐싱 후 반환

                    ### 영업시간
                    - 현재 Kakao Local 응답에는 영업시간이 없어 isOpen, businessStatusMessage가 null로 반환됩니다
                    - 영업시간 데이터가 없는 장소는 isOpen, businessStatusMessage가 null로 반환됩니다
                    """)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "주변 장소 검색 성공",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = NearbyPlaceSearchResponse.class),
                        examples = @ExampleObject(
                                name = "성공 응답",
                                summary = "카테고리별 장소 목록",
                                value = """
                                            {
                                              "code": "S100",
                                              "message": "요청이 성공적으로 처리되었습니다.",
                                              "data": {
                                                "categories": [
                                                  {
                                                    "category": "스터디카페",
                                                    "places": [
                                                      {
                                                        "id": 1,
                                                        "name": "토즈 홍대점",
                                                        "formattedAddress": "서울특별시 마포구 와우산로 94",
                                                        "latitude": 37.5563,
                                                        "longitude": 126.9236,
                                                        "kakaoPlaceUrl": "https://place.map.kakao.com/12345678",
                                                        "distanceFromBase": 350,
                                                        "isOpen": true,
                                                        "businessStatusMessage": "22:00에 영업 종료"
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "category": "카페",
                                                    "places": [
                                                      {
                                                        "id": 2,
                                                        "name": "스타벅스 홍대입구역점",
                                                        "formattedAddress": "서울특별시 마포구 양화로 160",
                                                        "latitude": 37.5571,
                                                        "longitude": 126.9246,
                                                        "kakaoPlaceUrl": "https://place.map.kakao.com/87654321",
                                                        "distanceFromBase": 120,
                                                        "isOpen": false,
                                                        "businessStatusMessage": "내일 08:00에 영업 시작"
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                        )
                )
        )
})
public @interface NearbyPlaceSearchApiDocs {
}
