package com.dnd.moyeolak.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    @Test
    void shouldNotDefineFixedServerUrl() {
        SwaggerConfig swaggerConfig = new SwaggerConfig();

        OpenAPI openAPI = swaggerConfig.openAPI();

        assertThat(openAPI.getServers()).isNull();
    }
}
