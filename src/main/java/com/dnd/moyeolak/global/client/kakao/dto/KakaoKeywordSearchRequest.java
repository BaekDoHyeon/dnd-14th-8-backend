package com.dnd.moyeolak.global.client.kakao.dto;

public record KakaoKeywordSearchRequest(
        String query,              // 필수: 검색어
        String x,                  // 중심 좌표 경도
        String y,                  // 중심 좌표 위도
        Integer radius,            // 반경 (0~20000m)
        String sort,               // 정렬 기준 (distance, accuracy)
        Integer size               // 한 페이지에 보여질 문서 수 (1~15)
) {
    private final static int DEFAULT_RADIUS = 50;
    private final static int DEFAULT_SIZE = 15;

    public static KakaoKeywordSearchRequest of(String query, String x, String y) {
        return new KakaoKeywordSearchRequest(query, x, y, DEFAULT_RADIUS, "distance", DEFAULT_SIZE);
    }

    public static KakaoKeywordSearchRequest ofNearby(String query, String x, String y) {
        return new KakaoKeywordSearchRequest(query, x, y, 1000, "distance", DEFAULT_SIZE);
    }
}
