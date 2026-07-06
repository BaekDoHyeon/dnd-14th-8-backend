package com.dnd.moyeolak.global.health.dto;

import java.util.List;

public record ExternalApiHealthResponse(
        boolean allHealthy,
        int totalCount,
        int healthyCount,
        List<ApiCheckResult> results
) {
    public static ExternalApiHealthResponse from(List<ApiCheckResult> results) {
        int healthyCount = (int) results.stream().filter(ApiCheckResult::healthy).count();
        return new ExternalApiHealthResponse(
                healthyCount == results.size(),
                results.size(),
                healthyCount,
                results
        );
    }

    public record ApiCheckResult(
            String name,
            boolean healthy,
            long latencyMs,
            String detail
    ) {}
}
