package com.dnd.moyeolak.domain.location.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "중간지점 추천 결과 타입")
public enum MidpointResultType {
    @Schema(description = "일반 중간지점 추천")
    NORMAL,

    @Schema(description = "출발지가 이미 가까워 후보역 간 이동시간 차이가 작은 추천")
    NEARBY_DEPARTURES
}
