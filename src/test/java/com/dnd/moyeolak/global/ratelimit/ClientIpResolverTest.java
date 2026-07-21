package com.dnd.moyeolak.global.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("X-Forwarded-For가 있으면 첫 번째 IP를 반환한다")
    void resolve_usesFirstForwardedForIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("X-Forwarded-For가 비어 있으면 X-Real-IP를 반환한다")
    void resolve_fallsBackToRealIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.20");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("프록시 헤더가 없으면 remoteAddr를 반환한다")
    void resolve_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("127.0.0.1");
    }
}
