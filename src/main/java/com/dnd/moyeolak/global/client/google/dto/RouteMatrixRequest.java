package com.dnd.moyeolak.global.client.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteMatrixRequest(
        List<MatrixWaypoint> origins,
        List<MatrixWaypoint> destinations,
        String travelMode,
        String departureTime
) {

    public static RouteMatrixRequest of(
            List<LatLng> origins,
            List<LatLng> destinations,
            String travelMode,
            String departureTime
    ) {
        return new RouteMatrixRequest(
                origins.stream().map(MatrixWaypoint::from).toList(),
                destinations.stream().map(MatrixWaypoint::from).toList(),
                travelMode,
                departureTime
        );
    }

    public record MatrixWaypoint(GoogleWaypoint waypoint) {

        static MatrixWaypoint from(LatLng latLng) {
            return new MatrixWaypoint(GoogleWaypoint.from(latLng));
        }
    }
}
