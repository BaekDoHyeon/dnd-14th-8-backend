package com.dnd.moyeolak.global.metrics;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 외부 API 실패를 계측 태그용 에러 유형 문자열로 분류한다.
 * BODY_ERROR는 예외가 아닌 본문 기반 실패(ODsay 999, 빈 응답 등)에 클라이언트가 직접 사용한다.
 */
public final class ExternalApiErrorType {

    public static final String CLIENT_4XX = "4xx";
    public static final String SERVER_5XX = "5xx";
    public static final String TIMEOUT = "timeout";
    public static final String BODY_ERROR = "body_error";
    public static final String UNKNOWN = "unknown";

    private ExternalApiErrorType() {
    }

    public static String classify(Throwable t) {
        if (t instanceof HttpClientErrorException) {
            return CLIENT_4XX;
        }
        if (t instanceof HttpServerErrorException) {
            return SERVER_5XX;
        }
        if (t instanceof ResourceAccessException) {
            return TIMEOUT;
        }
        return UNKNOWN;
    }
}
