package com.dnd.moyeolak.global.client.google.dto;

public record GoogleWaypoint(Location location) {

    public static GoogleWaypoint from(LatLng latLng) {
        return new GoogleWaypoint(new Location(latLng));
    }

    public record Location(LatLng latLng) {
    }
}
