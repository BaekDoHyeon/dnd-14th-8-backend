package com.dnd.moyeolak.global.client.mapglot.dto;

import java.util.List;

public record MapGlotRouteResponse(
        List<Route> routes
) {

    public record Route(
            int distance,
            int duration,
            Geometry geometry
    ) {}

    public record Geometry(
            String type,
            List<List<Double>> coordinates
    ) {}
}
