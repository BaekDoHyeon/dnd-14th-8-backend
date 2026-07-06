package com.dnd.moyeolak.domain.location.dto;

public record TransitRouteResult(
        int durationMinutes,
        int distanceMeters,
        boolean reachable
) {

    public static TransitRouteResult unreachable() {
        return new TransitRouteResult(999, 0, false);
    }
}
