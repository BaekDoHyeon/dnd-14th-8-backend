package com.dnd.moyeolak.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    @DisplayName("로컬 Vite 개발 서버의 127.0.0.1 Origin을 허용한다")
    void allowedOrigins_containsLocalViteLoopbackOrigin() {
        assertThat(CorsConfig.allowedOrigins()).contains("http://127.0.0.1:5173");
    }
}
