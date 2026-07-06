package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.TransitRouteResult;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.global.client.odsay.OdsayClient;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.station.entity.Station;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OdsayTransitRouteClient {

    private static final int ODSAY_FAILURE_SENTINEL = 999;

    private final OdsayClient odsayClient;

    public TransitRouteResult calculate(LocationVote vote, Station station) {
        OdsayPathInfo pathInfo = odsayClient.searchRoute(
                vote.getDepartureLat().doubleValue(),
                vote.getDepartureLng().doubleValue(),
                station.getLatitude(),
                station.getLongitude()
        );

        if (pathInfo == null || pathInfo.safeTotal() >= ODSAY_FAILURE_SENTINEL) {
            return TransitRouteResult.unreachable();
        }

        return new TransitRouteResult(pathInfo.safeTotal(), pathInfo.safeTotalDistance(), true);
    }
}
