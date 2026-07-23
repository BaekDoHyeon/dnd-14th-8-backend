package com.dnd.moyeolak.global.metrics.admin;

import com.dnd.moyeolak.global.metrics.stats.ExternalApiStatsService;
import com.dnd.moyeolak.global.metrics.stats.StatsRange;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiTrendsResponse;
import com.dnd.moyeolak.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 API 호출 통계 관리자 API. X-Admin-Token 헤더 인증(AdminTokenFilter)으로 보호된다.
 */
@Tag(name = "Admin - External API Stats", description = "외부 API 호출 통계 (관리자)")
@RestController
@RequestMapping("/api/admin/external-api")
@RequiredArgsConstructor
public class AdminExternalApiController {

    private final ExternalApiStatsService statsService;

    @Operation(summary = "외부 API 요약", description = "API별 오늘(KST) 호출·성공률·p95·429·한도 잔여·Google 비용")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ExternalApiSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(statsService.summary()));
    }

    @Operation(summary = "외부 API 추이", description = "range(24h|7d|30d)별 호출수·에러율·Google 비용 시계열")
    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<ExternalApiTrendsResponse>> trends(
            @RequestParam(defaultValue = "24h") String range) {
        return ResponseEntity.ok(ApiResponse.success(statsService.trends(StatsRange.from(range))));
    }
}
