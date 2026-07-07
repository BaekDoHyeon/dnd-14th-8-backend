package com.dnd.moyeolak.global.client.odsay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OdsayApiConfig {

    @Value("${odsay.api.key}")
    private String odsayApiKey;

    public String getOdsayApiKey() {
        return odsayApiKey;
    }

    @Bean("odsayRestTemplate")
    public RestTemplate odsayRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
