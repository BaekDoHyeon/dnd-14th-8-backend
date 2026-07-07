package com.dnd.moyeolak.global.client.odsay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OdsayPathInfo(
    @JsonProperty("totalTime")
    Integer totalTime,

    @JsonProperty("payment")
    Integer payment,

    @JsonProperty("busTransitCount")
    Integer busTransitCount,

    @JsonProperty("subwayTransitCount")
    Integer subwayTransitCount,

    @JsonProperty("totalDistance")
    Integer totalDistance,

    @JsonProperty("totalWalk")
    Integer totalWalk,

    @JsonProperty("totalStationCount")
    Integer totalStationCount
) {
    public int safeTotal() { return totalTime != null ? totalTime : 999; }
    public int safePayment() { return payment != null ? payment : 0; }
    public int safeBusTransit() { return busTransitCount != null ? busTransitCount : 0; }
    public int safeSubwayTransit() { return subwayTransitCount != null ? subwayTransitCount : 0; }
    public int safeTotalDistance() { return totalDistance != null ? totalDistance : 0; }
    public int safeTotalWalk() { return totalWalk != null ? totalWalk : 0; }
}
