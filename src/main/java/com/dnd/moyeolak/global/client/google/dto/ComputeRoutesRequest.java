package com.dnd.moyeolak.global.client.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComputeRoutesRequest(
        GoogleWaypoint origin,
        GoogleWaypoint destination,
        String travelMode,
        String departureTime
) {

    public static ComputeRoutesRequest of(
            LatLng origin,
            LatLng destination,
            String travelMode,
            String departureTime
    ) {
        return new ComputeRoutesRequest(
                GoogleWaypoint.from(origin),
                GoogleWaypoint.from(destination),
                travelMode,
                departureTime
        );
    }
}
