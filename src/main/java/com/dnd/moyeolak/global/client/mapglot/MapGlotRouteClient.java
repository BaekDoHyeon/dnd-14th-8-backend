package com.dnd.moyeolak.global.client.mapglot;

import com.dnd.moyeolak.domain.location.dto.DrivingRouteResult;
import com.dnd.moyeolak.global.client.mapglot.config.MapGlotApiConfig;
import com.dnd.moyeolak.global.client.mapglot.dto.MapGlotRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class MapGlotRouteClient {

    private static final String BASE_URL = "https://mapglot.com/api/v1/route";
    private static final String DRIVING_PROFILE = "driving";

    private final RestTemplate restTemplate;
    private final MapGlotApiConfig config;

    public MapGlotRouteClient(
            @Qualifier("mapGlotRestTemplate") RestTemplate restTemplate,
            MapGlotApiConfig config
    ) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public DrivingRouteResult calculateDriving(
            double originLat,
            double originLng,
            double targetLat,
            double targetLng
    ) {
        String url = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("profile", DRIVING_PROFILE)
                .queryParam("from", formatCoordinate(originLng, originLat))
                .queryParam("to", formatCoordinate(targetLng, targetLat))
                .queryParam("key", config.getMapGlotApiKey())
                .build()
                .toUriString();

        try {
            MapGlotRouteResponse response = restTemplate.getForObject(url, MapGlotRouteResponse.class);
            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                log.warn("MapGlot Route API 응답이 비어있습니다.");
                return DrivingRouteResult.unreachable();
            }

            MapGlotRouteResponse.Route route = response.routes().getFirst();
            return new DrivingRouteResult(route.duration(), route.distance(), true);
        } catch (RestClientException e) {
            log.warn("MapGlot Route API 호출 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return DrivingRouteResult.unreachable();
        }
    }

    private String formatCoordinate(double longitude, double latitude) {
        return Double.toString(longitude) + "," + Double.toString(latitude);
    }
}
