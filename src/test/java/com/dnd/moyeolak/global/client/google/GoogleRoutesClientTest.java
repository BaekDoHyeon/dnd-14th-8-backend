package com.dnd.moyeolak.global.client.google;

import com.dnd.moyeolak.domain.location.dto.TransitRouteDetailDto;
import com.dnd.moyeolak.domain.location.dto.TransitRouteResult;
import com.dnd.moyeolak.global.client.google.config.GoogleRoutesApiConfig;
import com.dnd.moyeolak.global.client.google.dto.LatLng;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.metrics.ExternalApiMetrics;
import com.dnd.moyeolak.global.response.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesClientTest {

    private static final String MATRIX_URL =
            "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";
    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private GoogleRoutesClient client;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        registry = new SimpleMeterRegistry();
        client = new GoogleRoutesClient(
                restTemplate, new GoogleRoutesApiConfig("test-google-key"), new ExternalApiMetrics(registry));
    }

    @Test
    @DisplayName("TRANSIT 매트릭스를 1회 호출하고 originIndex/destinationIndex 그리드로 파싱한다")
    void computesTransitMatrixInSingleRequest() {
        server.expect(requestTo(MATRIX_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-google-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "originIndex,destinationIndex,duration,distanceMeters,condition"))
                .andExpect(jsonPath("$.travelMode").value("TRANSIT"))
                .andExpect(jsonPath("$.origins.length()").value(2))
                .andExpect(jsonPath("$.destinations.length()").value(2))
                .andExpect(jsonPath("$.origins[0].waypoint.location.latLng.latitude").value(37.5))
                .andRespond(withSuccess("""
                        [
                          {"originIndex":0,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":9000,"duration":"1200s"},
                          {"originIndex":0,"destinationIndex":1,"condition":"ROUTE_NOT_FOUND"},
                          {"originIndex":1,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":10000,"duration":"1800s"},
                          {"originIndex":1,"destinationIndex":1,"condition":"ROUTE_EXISTS","distanceMeters":15000,"duration":"4800s"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<List<TransitRouteResult>> matrix = client.computeTransitMatrix(
                List.of(new LatLng(37.5, 127.0), new LatLng(37.55, 126.95)),
                List.of(new LatLng(37.5495, 126.9137), new LatLng(37.4979, 127.0276)),
                null);

        assertThat(matrix).hasSize(2);
        assertThat(matrix.get(0).get(0).durationMinutes()).isEqualTo(20);
        assertThat(matrix.get(0).get(0).distanceMeters()).isEqualTo(9000);
        assertThat(matrix.get(0).get(0).reachable()).isTrue();
        assertThat(matrix.get(0).get(1).reachable()).isFalse();
        assertThat(matrix.get(1).get(0).durationMinutes()).isEqualTo(30);
        assertThat(matrix.get(1).get(1).durationMinutes()).isEqualTo(80);
        server.verify();
    }

    @Test
    @DisplayName("엘리먼트가 100개를 넘으면 origin 기준으로 분할 요청하고 인덱스를 보정한다")
    void chunksRequestsWhenElementsExceedLimit() {
        // 11 origins x 10 destinations = 110 elements -> 10 + 1 origin으로 2회 요청
        server.expect(requestTo(MATRIX_URL))
                .andExpect(jsonPath("$.origins.length()").value(10))
                .andRespond(withSuccess("""
                        [{"originIndex":0,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":5000,"duration":"600s"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(MATRIX_URL))
                .andExpect(jsonPath("$.origins.length()").value(1))
                .andRespond(withSuccess("""
                        [{"originIndex":0,"destinationIndex":9,"condition":"ROUTE_EXISTS","distanceMeters":7000,"duration":"900s"}]
                        """, MediaType.APPLICATION_JSON));

        List<LatLng> origins = IntStream.range(0, 11)
                .mapToObj(i -> new LatLng(37.5 + i * 0.01, 127.0)).toList();
        List<LatLng> destinations = IntStream.range(0, 10)
                .mapToObj(i -> new LatLng(37.4 + i * 0.01, 126.9)).toList();

        List<List<TransitRouteResult>> matrix = client.computeTransitMatrix(origins, destinations, null);

        assertThat(matrix).hasSize(11);
        assertThat(matrix.get(0).get(0).durationMinutes()).isEqualTo(10);
        // 두 번째 요청의 originIndex 0은 전체 그리드에서 10번 origin이다
        assertThat(matrix.get(10).get(9).durationMinutes()).isEqualTo(15);
        assertThat(matrix.get(10).get(0).reachable()).isFalse(); // 응답에 없는 엘리먼트는 도달 불가
        server.verify();
    }

    @Test
    @DisplayName("매트릭스 요청 자체가 실패하면 GOOGLE_API_ERROR 예외가 발생한다")
    void throwsWhenMatrixRequestFails() {
        server.expect(requestTo(MATRIX_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.computeTransitMatrix(
                List.of(new LatLng(37.5, 127.0)),
                List.of(new LatLng(37.4979, 127.0276)),
                null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOOGLE_API_ERROR);
    }

    @Test
    @DisplayName("departureTime을 KST 기준 RFC3339 UTC로 변환해 전송하고 소수점 초 duration도 파싱한다")
    void sendsDepartureTimeAsRfc3339AndParsesFractionalSeconds() {
        server.expect(requestTo(MATRIX_URL))
                .andExpect(jsonPath("$.departureTime").value("2099-07-20T00:30:00Z"))
                .andRespond(withSuccess("""
                        [{"originIndex":0,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":5000,"duration":"1234.5s"}]
                        """, MediaType.APPLICATION_JSON));

        List<List<TransitRouteResult>> matrix = client.computeTransitMatrix(
                List.of(new LatLng(37.5, 127.0)),
                List.of(new LatLng(37.4979, 127.0276)),
                java.time.LocalDateTime.of(2099, 7, 20, 9, 30)); // KST 09:30 -> UTC 00:30

        assertThat(matrix.get(0).get(0).durationMinutes()).isEqualTo(21); // 1234.5s -> 1234s -> 21분
        server.verify();
    }

    @Test
    @DisplayName("과거 departureTime은 요청에서 생략되어 '지금 출발'로 처리된다")
    void omitsPastDepartureTime() {
        server.expect(requestTo(MATRIX_URL))
                .andExpect(jsonPath("$.departureTime").doesNotExist())
                .andRespond(withSuccess("""
                        [{"originIndex":0,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":5000,"duration":"600s"}]
                        """, MediaType.APPLICATION_JSON));

        List<List<TransitRouteResult>> matrix = client.computeTransitMatrix(
                List.of(new LatLng(37.5, 127.0)),
                List.of(new LatLng(37.4979, 127.0276)),
                java.time.LocalDateTime.of(2020, 1, 1, 9, 0));

        assertThat(matrix.get(0).get(0).reachable()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("단건 대중교통 경로에서 소요시간·거리·요금·환승·도보거리를 파싱한다")
    void computesTransitRouteDetail() {
        server.expect(requestTo(ROUTES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-google-key"))
                .andExpect(jsonPath("$.travelMode").value("TRANSIT"))
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(37.55))
                .andRespond(withSuccess("""
                        {
                          "routes": [
                            {
                              "duration": "3600s",
                              "distanceMeters": 21400,
                              "legs": [
                                {
                                  "steps": [
                                    {"travelMode": "WALK", "distanceMeters": 300},
                                    {"travelMode": "TRANSIT", "distanceMeters": 12000},
                                    {"travelMode": "WALK", "distanceMeters": 100},
                                    {"travelMode": "TRANSIT", "distanceMeters": 9000}
                                  ]
                                }
                              ],
                              "travelAdvisory": {
                                "transitFare": {"currencyCode": "KRW", "units": "1500"}
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TransitRouteDetailDto detail = client.computeTransitRoute(
                new LatLng(37.55, 126.97), new LatLng(37.56, 126.80), null);

        assertThat(detail.durationMinutes()).isEqualTo(60);
        assertThat(detail.distanceMeters()).isEqualTo(21400);
        assertThat(detail.fare()).isEqualTo(1500);
        assertThat(detail.transferCount()).isEqualTo(1); // TRANSIT step 2개 - 1
        assertThat(detail.walkDistanceMeters()).isEqualTo(400);
        server.verify();
    }

    @Test
    @DisplayName("단건 경로 응답이 비어있으면 GOOGLE_API_ERROR 예외가 발생한다")
    void throwsWhenRouteResponseIsEmpty() {
        server.expect(requestTo(ROUTES_URL))
                .andRespond(withSuccess("{\"routes\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.computeTransitRoute(
                new LatLng(37.55, 126.97), new LatLng(37.56, 126.80), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOOGLE_API_ERROR);
    }

    @Test
    @DisplayName("매트릭스 성공 시 성공 카운터와 route_matrix 과금 단위(엘리먼트 수)가 기록된다")
    void recordsMetricsOnMatrixSuccess() {
        server.expect(requestTo(MATRIX_URL))
                .andRespond(withSuccess("""
                        [{"originIndex":0,"destinationIndex":0,"condition":"ROUTE_EXISTS","distanceMeters":9000,"duration":"1200s"}]
                        """, MediaType.APPLICATION_JSON));

        // 2 origins x 2 destinations = 4 elements
        client.computeTransitMatrix(
                List.of(new LatLng(37.5, 127.0), new LatLng(37.55, 126.95)),
                List.of(new LatLng(37.5495, 126.9137), new LatLng(37.4979, 127.0276)),
                null);

        assertThat(registry.get("external.api.calls")
                .tags("api", "google_routes", "outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("external.api.google.billable_units")
                .tags("sku", "route_matrix").counter().count()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("단건 경로 호출이 5xx로 실패하면 failure 카운터가 error_type=5xx로 기록된다")
    void recordsMetricsOnRouteServerError() {
        server.expect(requestTo(ROUTES_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.computeTransitRoute(
                new LatLng(37.55, 126.97), new LatLng(37.56, 126.80), null))
                .isInstanceOf(BusinessException.class);

        assertThat(registry.get("external.api.calls")
                .tags("api", "google_routes", "outcome", "failure", "error_type", "5xx")
                .counter().count()).isEqualTo(1.0);
    }
}
