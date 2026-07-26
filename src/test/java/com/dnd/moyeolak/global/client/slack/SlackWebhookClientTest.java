package com.dnd.moyeolak.global.client.slack;

import com.dnd.moyeolak.global.client.slack.config.SlackApiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;

class SlackWebhookClientTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T000/B000/xxx";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private SlackApiConfig config;
    private SlackWebhookClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        config = new SlackApiConfig();
        ReflectionTestUtils.setField(config, "webhookUrl", WEBHOOK_URL);
        client = new SlackWebhookClient(restTemplate, config);
    }

    @Test
    @DisplayName("webhook URL로 text 메시지를 JSON POST한다")
    void sendsMessageToWebhookUrl() {
        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("hello slack")))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        boolean sent = client.send("hello slack");

        server.verify();
        assertThat(sent).isTrue();
    }

    @Test
    @DisplayName("webhook URL이 비어있으면 아무 요청도 보내지 않는다")
    void doesNothingWhenWebhookUrlBlank() {
        ReflectionTestUtils.setField(config, "webhookUrl", "");

        boolean sent = client.send("hello slack");

        server.verify();
        assertThat(sent).isTrue();
    }

    @Test
    @DisplayName("Slack 서버가 오류를 응답하면 예외를 던지지 않고 false를 반환한다")
    void returnsFalseWhenServerRespondsWithError() {
        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(POST))
                .andRespond(withServerError());

        boolean sent = client.send("hello slack");

        server.verify();
        assertThat(sent).isFalse();
    }
}
