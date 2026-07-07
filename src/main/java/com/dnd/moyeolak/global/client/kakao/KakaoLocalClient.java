package com.dnd.moyeolak.global.client.kakao;

import com.dnd.moyeolak.global.client.kakao.config.KakaoApiConfig;
import com.dnd.moyeolak.global.client.kakao.dto.CategorySearchResponse;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoLocalResponse;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoKeywordSearchRequest;
import com.dnd.moyeolak.global.client.kakao.dto.SubwayStation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Component
public class KakaoLocalClient {

    private final RestTemplate kakaoRestTemplate;
    private final KakaoApiConfig kakaoApiConfig;

    private static final String KAKAO_LOCAL_BASE_URL = "https://dapi.kakao.com/v2/local/search";
    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String SUBWAY_CATEGORY_CODE = "SW8";

    public KakaoLocalClient(
        @Qualifier("kakaoRestTemplate") RestTemplate kakaoRestTemplate,
        KakaoApiConfig kakaoApiConfig
    ) {
        this.kakaoRestTemplate = kakaoRestTemplate;
        this.kakaoApiConfig = kakaoApiConfig;
    }

    public List<SubwayStation> findNearestSubwayStations(
        double centerLat,
        double centerLng,
        int radius,
        int limit
    ) {
        String url = String.format(
            "%s?category_group_code=%s&x=%f&y=%f&radius=%d&sort=distance",
            KAKAO_LOCAL_API_URL,
            SUBWAY_CATEGORY_CODE,
            centerLng,
            centerLat,
            radius
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiConfig.getKakaoApiKey());

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalResponse> response = kakaoRestTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KakaoLocalResponse.class
            );

            if (response.getBody() == null) {
                log.warn("Kakao Local API 응답이 비어있습니다.");
                return List.of();
            }

            return response.getBody()
                .documents()
                .stream()
                .limit(limit)
                .map(doc -> new SubwayStation(
                    doc.placeName(),
                    doc.addressName(),
                    doc.roadAddressName(),
                    Double.parseDouble(doc.y()),
                    Double.parseDouble(doc.x()),
                    Integer.parseInt(doc.distance())
                ))
                .toList();

        } catch (Exception e) {
            log.error("Kakao Local API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("지하철역 검색에 실패했습니다.", e);
        }
    }

    /**
     * 키워드를 사용하여 장소를 검색한다.
     */
    public CategorySearchResponse searchByKeyword(KakaoKeywordSearchRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(KAKAO_LOCAL_BASE_URL + "/keyword.json")
                .queryParam("query", request.query());

        if (request.x() != null && request.y() != null) {
            builder.queryParam("x", request.x());
            builder.queryParam("y", request.y());
        }
        if (request.radius() != null) {
            builder.queryParam("radius", request.radius());
        }
        if (request.sort() != null) {
            builder.queryParam("sort", request.sort());
        }
        if (request.size() != null) {
            builder.queryParam("size", request.size());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiConfig.getKakaoApiKey());

        HttpEntity<String> entity = new HttpEntity<>(headers);

        URI uri = builder.build().encode().toUri();

        ResponseEntity<CategorySearchResponse> response = kakaoRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                CategorySearchResponse.class
        );

        return response.getBody();
    }
}
