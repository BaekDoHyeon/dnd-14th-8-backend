package com.dnd.moyeolak.global.client.mapglot.config;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@NoArgsConstructor
public class MapGlotApiConfig {

    @Value("${mapglot.api.key}")
    private String mapGlotApiKey;

    public MapGlotApiConfig(String mapGlotApiKey) {
        this.mapGlotApiKey = mapGlotApiKey;
    }

    public String getMapGlotApiKey() {
        return mapGlotApiKey;
    }

    @Bean("mapGlotRestTemplate")
    public RestTemplate mapGlotRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
