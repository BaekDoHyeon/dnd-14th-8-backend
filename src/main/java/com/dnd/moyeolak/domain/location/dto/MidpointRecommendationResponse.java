package com.dnd.moyeolak.domain.location.dto;

import com.dnd.moyeolak.domain.location.enums.MidpointResultType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "중간지점 추천 결과")
public record MidpointRecommendationResponse(
        @Schema(description = "모든 참여자의 중심 좌표")
        CenterPointDto centerPoint,

        @Schema(description = "추천 지하철역 목록 (최대 3개, 평균 이동시간 오름차순)")
        List<StationRecommendationDto> recommendations,

        @Schema(description = "출발 시간 (미입력 시 null)", example = "2026-02-18T10:30:00")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalDateTime departureTime,

        @Schema(description = "출발지를 등록한 참여자 수", example = "3")
        int registeredCount,

        @Schema(description = "전체 참여자 수", example = "10")
        int totalCount,

        @Schema(description = "중간지점 추천 결과 타입", example = "NORMAL")
        MidpointResultType resultType
) {}
