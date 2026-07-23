package com.dnd.moyeolak.global.metrics;

/**
 * 통계를 수집하는 외부 API 종류. tag() 값이 Micrometer 태그/스냅샷 저장 키로 사용된다.
 */
public enum ExternalApi {
    ODSAY("odsay"),
    KAKAO_LOCAL("kakao_local"),
    KAKAO_DIRECTIONS("kakao_directions"),
    GOOGLE_ROUTES("google_routes");

    private final String tag;

    ExternalApi(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
