package com.dnd.moyeolak.global.client.odsay;

import com.dnd.moyeolak.global.client.odsay.config.OdsayApiConfig;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.metrics.ExternalApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OdsayClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MeterRegistry registry;
    private OdsayClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        registry = new SimpleMeterRegistry();
        OdsayApiConfig config = new OdsayApiConfig();
        ReflectionTestUtils.setField(config, "odsayApiKey", "test-key");
        client = new OdsayClient(restTemplate, config, new ExternalApiMetrics(registry));
    }

    @Test
    @DisplayName("정상 경로 응답 시 success 카운터와 레이턴시가 기록된다")
    void recordsSuccessOnValidResponse() {
        server.expect(requestTo(containsString("searchPubTransPathT")))
                .andRespond(withSuccess("""
                        {"result":{"path":[{"info":{"totalTime":45,"payment":1500,"busTransitCount":1,
                        "subwayTransitCount":1,"totalDistance":12000,"totalWalk":300,"totalStationCount":10},
                        "subPath":[]}]}}
                        """, MediaType.APPLICATION_JSON));

        OdsayPathInfo info = client.searchRoute(37.5, 127.0, 37.4979, 127.0276);

        assertThat(info.totalTime()).isEqualTo(45);
        assertThat(registry.get("external.api.calls")
                .tags("api", "odsay", "outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("external.api.latency")
                .tags("api", "odsay").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 경로 응답 시 body_error 실패로 기록되고 999 센티넬을 반환한다")
    void recordsBodyErrorOnEmptyResponse() {
        server.expect(requestTo(containsString("searchPubTransPathT")))
                .andRespond(withSuccess("{\"result\":{\"path\":[]}}", MediaType.APPLICATION_JSON));

        OdsayPathInfo info = client.searchRoute(37.5, 127.0, 37.4979, 127.0276);

        assertThat(info.totalTime()).isEqualTo(999);
        assertThat(registry.get("external.api.calls")
                .tags("api", "odsay", "outcome", "failure", "error_type", "body_error")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("5xx 응답 시 failure(5xx)로 기록되고 999 센티넬을 반환한다")
    void recordsServerErrorFailure() {
        server.expect(requestTo(containsString("searchPubTransPathT"))).andRespond(withServerError());

        OdsayPathInfo info = client.searchRoute(37.5, 127.0, 37.4979, 127.0276);

        assertThat(info.totalTime()).isEqualTo(999);
        assertThat(registry.get("external.api.calls")
                .tags("api", "odsay", "outcome", "failure", "error_type", "5xx")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("429 응답 시 rate_limited 카운터가 기록되고 재시도한다")
    void recordsRateLimitedOn429() {
        server.expect(requestTo(containsString("searchPubTransPathT")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(containsString("searchPubTransPathT")))
                .andRespond(withServerError()); // 재시도는 5xx로 종료 (성공 JSON 불필요)

        client.searchRoute(37.5, 127.0, 37.4979, 127.0276);

        assertThat(registry.get("external.api.rate_limited")
                .tags("api", "odsay").counter().count()).isEqualTo(1.0);
    }
}
