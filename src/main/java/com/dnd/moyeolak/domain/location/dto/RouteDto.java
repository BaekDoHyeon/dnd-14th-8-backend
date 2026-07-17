package com.dnd.moyeolak.domain.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "참여자별 이동 경로 정보")
@Builder
public record RouteDto(
        @Schema(description = "참여자 ID", example = "15")
        Long participantId,
        @Schema(description = "참여자 이름", example = "김철수")
        String departureName,

        @Schema(description = "출발지 주소", example = "수원시 장안구 영화동")
        String departureAddress,

        @Schema(description = "대중교통 이동시간 (분)", example = "92")
        int transitDuration,

        @Schema(description = "대중교통 이동거리 (미터)", example = "21400")
        int transitDistance,

        @Schema(description = "대중교통 도달 가능 여부 (false면 이동시간은 999 고정값)", example = "true")
        boolean transitReachable,

        @Schema(description = "자가용 이동시간 (분)", example = "45")
        int drivingDuration,

        @Schema(description = "자가용 이동거리 (미터)", example = "18200")
        int drivingDistance,

        @Schema(description = "자가용 도달 가능 여부 (false면 이동시간은 999 고정값)", example = "true")
        boolean drivingReachable
) {}
