package com.dnd.moyeolak.domain.location.dto;

public record DrivingRouteResult(
        int durationSeconds,
        int distanceMeters,
        boolean reachable
) {

    private static final int UNREACHABLE_DURATION_SECONDS = 999 * 60;

    public static DrivingRouteResult unreachable() {
        return new DrivingRouteResult(UNREACHABLE_DURATION_SECONDS, 0, false);
    }
}
