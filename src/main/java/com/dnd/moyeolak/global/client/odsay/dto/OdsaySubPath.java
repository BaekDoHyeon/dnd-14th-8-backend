package com.dnd.moyeolak.global.client.odsay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OdsaySubPath(
    @JsonProperty("trafficType")
    Integer trafficType,

    @JsonProperty("distance")
    Integer distance,

    @JsonProperty("sectionTime")
    Integer sectionTime,

    @JsonProperty("stationCount")
    Integer stationCount,

    @JsonProperty("lane")
    List<Lane> lane,

    @JsonProperty("startName")
    String startName,

    @JsonProperty("endName")
    String endName
) {
    public record Lane(
        @JsonProperty("name")
        String name,

        @JsonProperty("subwayCode")
        Integer subwayCode
    ) {}
}
