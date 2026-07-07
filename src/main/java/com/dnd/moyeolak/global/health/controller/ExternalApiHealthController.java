package com.dnd.moyeolak.global.health.controller;

import com.dnd.moyeolak.global.health.dto.ExternalApiHealthResponse;
import com.dnd.moyeolak.global.health.service.ExternalApiHealthService;
import com.dnd.moyeolak.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "헬스체크 API")
@RestController
@RequestMapping("/api/test/health")
@Profile("local")
@RequiredArgsConstructor
public class ExternalApiHealthController {

    private final ExternalApiHealthService externalApiHealthService;

    @Operation(
            summary = "외부 API 헬스체크 (로컬 전용)",
            description = "Kakao/Google Routes 외부 API에 최소 비용 호출을 1건씩 보내 키·쿼터·연결 상태를 확인합니다. "
                    + "일일 쿼터가 소모되므로 필요할 때만 수동으로 호출하세요."
    )
    @GetMapping("/external")
    public ResponseEntity<ApiResponse<ExternalApiHealthResponse>> checkExternalApis() {
        ExternalApiHealthResponse response = externalApiHealthService.checkExternalApis();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
