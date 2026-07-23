package com.dnd.moyeolak.global.client.kakao;

import com.dnd.moyeolak.global.client.kakao.config.KakaoDirectionsApiConfig;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoDirectionsClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MeterRegistry registry;
    private KakaoDirectionsClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        registry = new SimpleMeterRegistry();
        KakaoDirectionsApiConfig config = new KakaoDirectionsApiConfig();
        ReflectionTestUtils.setField(config, "kakaoDirectionsApiKey", "test-key");
        client = new KakaoDirectionsClient(restTemplate, config, new ExternalApiMetrics(registry));
    }

    @Test
    @DisplayName("경로 요약 응답 성공 시 success 카운터와 레이턴시가 기록된다")
    void recordsSuccessOnValidResponse() {
        server.expect(requestTo(containsString("directions")))
                .andRespond(withSuccess("""
                        {"routes":[{"summary":{"distance":10000,"duration":1200,"fare":{"toll":0,"taxi":8000}}}]}
                        """, MediaType.APPLICATION_JSON));

        KakaoDirectionsResponse.Summary summary = client.requestDrivingRoute(
                37.5, 127.0, 37.4979, 127.0276, null);

        assertThat(summary).isNotNull();
        assertThat(summary.duration()).isEqualTo(1200);
        assertThat(registry.get("external.api.calls")
                .tags("api", "kakao_directions", "outcome", "success").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("5xx 응답 시 failure(5xx)로 기록되고 null을 반환한다")
    void recordsServerErrorFailure() {
        server.expect(requestTo(containsString("directions"))).andRespond(withServerError());

        KakaoDirectionsResponse.Summary summary = client.requestDrivingRoute(
                37.5, 127.0, 37.4979, 127.0276, null);

        assertThat(summary).isNull();
        assertThat(registry.get("external.api.calls")
                .tags("api", "kakao_directions", "outcome", "failure", "error_type", "5xx")
                .counter().count()).isEqualTo(1.0);
    }
}
