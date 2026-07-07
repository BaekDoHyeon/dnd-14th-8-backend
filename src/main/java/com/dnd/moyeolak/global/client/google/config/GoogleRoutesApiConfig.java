package com.dnd.moyeolak.global.client.google.config;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@NoArgsConstructor
public class GoogleRoutesApiConfig {

    @Value("${google.api.key}")
    private String googleApiKey;

    public GoogleRoutesApiConfig(String googleApiKey) {
        this.googleApiKey = googleApiKey;
    }

    public String getGoogleApiKey() {
        return googleApiKey;
    }

    @Bean("googleRoutesRestTemplate")
    public RestTemplate googleRoutesRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }
}
