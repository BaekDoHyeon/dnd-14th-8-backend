package com.dnd.moyeolak.global.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteMatrixEntry(
        Integer originIndex,
        Integer destinationIndex,
        String condition,
        Integer distanceMeters,
        String duration
) {

    private static final String ROUTE_EXISTS = "ROUTE_EXISTS";

    public boolean routeExists() {
        return ROUTE_EXISTS.equals(condition)
                && originIndex != null && destinationIndex != null && duration != null;
    }

    public long durationSeconds() {
        return (long) Double.parseDouble(duration.replace("s", ""));
    }

    public int safeDistanceMeters() {
        return distanceMeters != null ? distanceMeters : 0;
    }
}
