package com.dnd.moyeolak.global.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Test
    @DisplayName("요청 ID가 있으면 응답 헤더와 access log에 같은 값을 남긴다")
    void shouldUseIncomingRequestIdInResponseHeaderAndAccessLog(CapturedOutput output) throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/participants");
        request.addHeader(REQUEST_ID_HEADER, "request-id-123");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChangingChain(201));

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isEqualTo("request-id-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(output.getOut())
                .contains("http_request")
                .contains("method=GET")
                .contains("path=/api/participants")
                .contains("status=201")
                .contains("clientIp=203.0.113.10")
                .contains("requestId=request-id-123");
    }

    @Test
    @DisplayName("요청 ID가 없으면 새 요청 ID를 생성해 응답 헤더에 내려준다")
    void shouldGenerateRequestIdWhenHeaderIsMissing() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/meetings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChangingChain(200));

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("헬스체크와 actuator 요청은 access log를 남기지 않는다")
    void shouldSkipNoisyHealthCheckAccessLogs(CapturedOutput output) throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChangingChain(200));

        assertThat(response.getHeader(REQUEST_ID_HEADER)).isNotBlank();
        assertThat(output.getOut()).doesNotContain("http_request");
    }

    private FilterChain statusChangingChain(int status) {
        return (request, response) -> ((MockHttpServletResponse) response).setStatus(status);
    }
}
