package com.dnd.moyeolak.global.client.mapglot;

import com.dnd.moyeolak.domain.location.dto.DrivingRouteResult;
import com.dnd.moyeolak.global.client.mapglot.config.MapGlotApiConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MapGlotRouteClientTest {

    @Test
    @DisplayName("MapGlot route API를 lon,lat 좌표 순서와 driving profile로 호출하고 duration/distance를 파싱한다")
    void calculatesDrivingRouteWithLongitudeLatitudeOrder() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MapGlotApiConfig config = new MapGlotApiConfig("test-mapglot-key");
        MapGlotRouteClient client = new MapGlotRouteClient(restTemplate, config);

        server.expect(requestTo("https://mapglot.com/api/v1/route?profile=driving&from=126.978,37.5665&to=127.0276,37.4979&key=test-mapglot-key"))
                .andExpect(queryParam("profile", "driving"))
                .andExpect(queryParam("from", "126.978,37.5665"))
                .andExpect(queryParam("to", "127.0276,37.4979"))
                .andRespond(withSuccess("""
                        {
                          "routes": [
                            {
                              "distance": 8500,
                              "duration": 1260,
                              "geometry": {
                                "type": "LineString",
                                "coordinates": []
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DrivingRouteResult result = client.calculateDriving(
                37.5665, 126.9780,
                37.4979, 127.0276
        );

        assertThat(result.reachable()).isTrue();
        assertThat(result.durationSeconds()).isEqualTo(1260);
        assertThat(result.distanceMeters()).isEqualTo(8500);
        server.verify();
    }
}
