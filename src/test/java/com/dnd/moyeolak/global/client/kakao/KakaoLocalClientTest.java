package com.dnd.moyeolak.global.client.kakao;

import com.dnd.moyeolak.global.client.kakao.config.KakaoApiConfig;
import com.dnd.moyeolak.global.client.kakao.dto.SubwayStation;
import com.dnd.moyeolak.global.metrics.ExternalApiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MeterRegistry registry;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        registry = new SimpleMeterRegistry();
        KakaoApiConfig config = new KakaoApiConfig();
        ReflectionTestUtils.setField(config, "kakaoApiKey", "test-key");
        client = new KakaoLocalClient(restTemplate, config, new ExternalApiMetrics(registry));
    }

    @Test
    @DisplayName("지하철역 조회 성공 시 success 카운터와 레이턴시가 기록된다")
    void recordsSuccessOnValidResponse() {
        server.expect(requestTo(containsString("category.json")))
                .andRespond(withSuccess("""
                        {"documents":[{"place_name":"강남역","address_name":"서울 강남구",
                        "road_address_name":"서울 강남구 테헤란로","x":"127.0276","y":"37.4979","distance":"100"}],
                        "meta":{"total_count":1,"pageable_count":1,"is_end":true}}
                        """, MediaType.APPLICATION_JSON));

        List<SubwayStation> stations = client.findNearestSubwayStations(37.4979, 127.0276, 1000, 1);

        assertThat(stations).hasSize(1);
        assertThat(registry.get("external.api.calls")
                .tags("api", "kakao_local", "outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("external.api.latency")
                .tags("api", "kakao_local").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("5xx 응답 시 failure(5xx)로 기록되고 예외가 전파된다")
    void recordsServerErrorFailure() {
        server.expect(requestTo(containsString("category.json"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.findNearestSubwayStations(37.4979, 127.0276, 1000, 1))
                .isInstanceOf(RuntimeException.class);

        assertThat(registry.get("external.api.calls")
                .tags("api", "kakao_local", "outcome", "failure", "error_type", "5xx")
                .counter().count()).isEqualTo(1.0);
    }
}
