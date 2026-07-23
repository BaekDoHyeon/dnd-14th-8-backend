package com.dnd.moyeolak.global.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiErrorTypeTest {

    @Test
    @DisplayName("4xx 응답 예외는 4xx로 분류된다")
    void classifiesClientError() {
        Throwable t = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "429", null, null, null);
        assertThat(ExternalApiErrorType.classify(t)).isEqualTo("4xx");
    }

    @Test
    @DisplayName("5xx 응답 예외는 5xx로 분류된다")
    void classifiesServerError() {
        Throwable t = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "500", null, null, null);
        assertThat(ExternalApiErrorType.classify(t)).isEqualTo("5xx");
    }

    @Test
    @DisplayName("네트워크/타임아웃 예외는 timeout으로 분류된다")
    void classifiesTimeout() {
        Throwable t = new ResourceAccessException("timeout", new SocketTimeoutException());
        assertThat(ExternalApiErrorType.classify(t)).isEqualTo("timeout");
    }

    @Test
    @DisplayName("그 외 예외는 unknown으로 분류된다")
    void classifiesUnknown() {
        assertThat(ExternalApiErrorType.classify(new IllegalStateException())).isEqualTo("unknown");
    }
}
