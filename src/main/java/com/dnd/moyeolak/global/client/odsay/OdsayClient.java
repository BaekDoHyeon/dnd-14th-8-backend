package com.dnd.moyeolak.global.client.odsay;

import tools.jackson.databind.ObjectMapper;
import com.dnd.moyeolak.global.client.odsay.config.OdsayApiConfig;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathResponse;
import com.dnd.moyeolak.global.metrics.ExternalApi;
import com.dnd.moyeolak.global.metrics.ExternalApiErrorType;
import com.dnd.moyeolak.global.metrics.ExternalApiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class OdsayClient {

    private final RestTemplate odsayRestTemplate;
    private final OdsayApiConfig odsayApiConfig;
    private final ExternalApiMetrics metrics;

    private static final String ODSAY_PATH_API_URL = "https://api.odsay.com/v1/api/searchPubTransPathT";
    private static final int MAX_CONCURRENT_REQUESTS = 5;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    private final Semaphore rateLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);

    public OdsayClient(
        @Qualifier("odsayRestTemplate") RestTemplate odsayRestTemplate,
        OdsayApiConfig odsayApiConfig,
        ExternalApiMetrics metrics
    ) {
        this.odsayRestTemplate = odsayRestTemplate;
        this.odsayApiConfig = odsayApiConfig;
        this.metrics = metrics;
    }

    public OdsayPathInfo searchRoute(
        double startLat,
        double startLng,
        double endLat,
        double endLng
    ) {
        String url = UriComponentsBuilder.fromUriString(ODSAY_PATH_API_URL)
            .queryParam("lang", 0)
            .queryParam("SX", startLng)
            .queryParam("SY", startLat)
            .queryParam("EX", endLng)
            .queryParam("EY", endLat)
            .queryParam("SearchType", 0)
            .queryParam("apiKey", odsayApiConfig.getOdsayApiKey())
            .build()
            .toUriString();

        log.debug("ODsay API 요청: startLat={}, startLng={}, endLat={}, endLng={}",
            startLat, startLng, endLat, endLng);

        long startNanos = System.nanoTime();
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                rateLimiter.acquire();
                try {
                    String rawResponse = odsayRestTemplate.getForObject(url, String.class);
                    log.info("ODsay API raw 응답 (길이: {})", rawResponse != null ? rawResponse.length() : 0);

                    ObjectMapper mapper = new ObjectMapper();
                    OdsayPathResponse response = mapper.readValue(rawResponse, OdsayPathResponse.class);

                    if (response != null && response.result() != null && !response.result().path().isEmpty()) {
                        metrics.recordSuccess(ExternalApi.ODSAY, elapsedSince(startNanos));
                        return response.result().path().get(0).info();
                    }

                    log.warn("ODsay API 응답이 비어있습니다.");
                    metrics.recordFailure(ExternalApi.ODSAY, ExternalApiErrorType.BODY_ERROR, elapsedSince(startNanos));
                    return new OdsayPathInfo(999, 0, 0, 0, 0, 0, 0);
                } catch (HttpClientErrorException.TooManyRequests e) {
                    log.warn("ODsay API 429 rate limit, 재시도 {}/{}", attempt + 1, MAX_RETRIES);
                    metrics.recordRateLimited(ExternalApi.ODSAY);
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    Thread.sleep(backoff);
                    // finally 실행 후 다음 attempt로 계속
                } finally {
                    rateLimiter.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("ODsay API 호출 중 인터럽트 발생");
                metrics.recordFailure(ExternalApi.ODSAY, ExternalApiErrorType.UNKNOWN, elapsedSince(startNanos));
                return new OdsayPathInfo(999, 0, 0, 0, 0, 0, 0);
            } catch (Exception e) {
                log.error("ODsay API 호출 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                metrics.recordFailure(ExternalApi.ODSAY, ExternalApiErrorType.classify(e), elapsedSince(startNanos));
                return new OdsayPathInfo(999, 0, 0, 0, 0, 0, 0);
            }
        }

        log.error("ODsay API 최대 재시도 횟수 초과");
        metrics.recordFailure(ExternalApi.ODSAY, ExternalApiErrorType.CLIENT_4XX, elapsedSince(startNanos));
        return new OdsayPathInfo(999, 0, 0, 0, 0, 0, 0);
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
