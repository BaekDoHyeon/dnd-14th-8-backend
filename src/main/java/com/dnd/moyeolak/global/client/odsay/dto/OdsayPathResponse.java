package com.dnd.moyeolak.global.client.odsay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OdsayPathResponse(
    @JsonProperty("result")
    Result result
) {
    public record Result(
        @JsonProperty("path")
        List<Path> path
    ) {}

    public record Path(
        @JsonProperty("info")
        OdsayPathInfo info,

        @JsonProperty("subPath")
        List<OdsaySubPath> subPath
    ) {}
}
