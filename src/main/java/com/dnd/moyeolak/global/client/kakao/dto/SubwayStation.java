package com.dnd.moyeolak.global.client.kakao.dto;

public record SubwayStation(
    String name,
    String address,
    String roadAddress,
    double latitude,
    double longitude,
    int distanceFromCenter
) {}
