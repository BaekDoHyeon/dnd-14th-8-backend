package com.dnd.moyeolak.global.client.slack;

import com.dnd.moyeolak.global.client.slack.config.SlackApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class SlackWebhookClient {

    private final RestTemplate slackRestTemplate;
    private final SlackApiConfig slackApiConfig;

    public SlackWebhookClient(
            @Qualifier("slackRestTemplate") RestTemplate slackRestTemplate,
            SlackApiConfig slackApiConfig
    ) {
        this.slackRestTemplate = slackRestTemplate;
        this.slackApiConfig = slackApiConfig;
    }

    public boolean send(String message) {
        String webhookUrl = slackApiConfig.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("SLACK_WEBHOOK_URL 미설정 - 알림 전송 생략: {}", message);
            return true;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", message), headers);
            slackRestTemplate.postForEntity(webhookUrl, entity, String.class);
            return true;
        } catch (Exception e) {
            log.error("Slack 알림 전송 실패: {}", e.getClass().getName());
            return false;
        }
    }
}
