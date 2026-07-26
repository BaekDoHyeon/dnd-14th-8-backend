package com.dnd.moyeolak.global.client.slack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SlackApiConfig {

    @Value("${external-api.alert.slack-webhook-url:}")
    private String webhookUrl;

    public String getWebhookUrl() {
        return webhookUrl;
    }

    @Bean("slackRestTemplate")
    public RestTemplate slackRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
