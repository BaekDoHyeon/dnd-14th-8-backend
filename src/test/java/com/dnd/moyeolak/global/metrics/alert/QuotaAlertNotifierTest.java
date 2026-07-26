package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import com.dnd.moyeolak.global.metrics.stats.ExternalApiStatsService;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class QuotaAlertNotifierTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ExternalApiStatsService statsService;
    private SlackWebhookClient slackWebhookClient;
    private ExternalApiAlertProperties properties;
    private Clock clock;
    private QuotaAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        statsService = mock(ExternalApiStatsService.class);
        slackWebhookClient = mock(SlackWebhookClient.class);
        properties = new ExternalApiAlertProperties();
        properties.setThresholdPercent(20.0);
        clock = Clock.fixed(Instant.parse("2026-07-26T01:00:00Z"), KST);
        notifier = new QuotaAlertNotifier(statsService, slackWebhookClient, properties, clock);
        when(slackWebhookClient.send(anyString())).thenReturn(true);
    }

    private ApiSummary summary(String api, Long dailyQuota, Long quotaRemaining) {
        return new ApiSummary(api, 0, 0, 0, 0.0, 0, 0.0, dailyQuota, quotaRemaining, null);
    }

    @Test
    @DisplayName("잔여 비율이 임계치 미만이면 Slack 알림을 보낸다")
    void notifiesWhenBelowThreshold() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("odsay", 1000L, 100L)))); // 10% 남음

        notifier.checkAndNotify();

        verify(slackWebhookClient).send(contains("odsay"));
    }

    @Test
    @DisplayName("잔여 비율이 임계치 이상이면 알림을 보내지 않는다")
    void doesNotNotifyWhenAboveThreshold() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("odsay", 1000L, 500L)))); // 50% 남음

        notifier.checkAndNotify();

        verify(slackWebhookClient, never()).send(anyString());
    }

    @Test
    @DisplayName("dailyQuota가 없는 API(Google Routes)는 검사 대상에서 제외한다")
    void skipsApiWithoutDailyQuota() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("google_routes", null, null))));

        notifier.checkAndNotify();

        verify(slackWebhookClient, never()).send(anyString());
    }

    @Test
    @DisplayName("같은 날 같은 API는 한 번만 알림을 보낸다")
    void deduplicatesWithinSameDay() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("odsay", 1000L, 100L))));

        notifier.checkAndNotify();
        notifier.checkAndNotify();

        verify(slackWebhookClient, times(1)).send(anyString());
    }

    @Test
    @DisplayName("날짜가 바뀌면 같은 API도 다시 알림을 보낸다")
    void resendsAfterDateChanges() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("odsay", 1000L, 100L))));
        notifier.checkAndNotify();

        Clock nextDay = Clock.fixed(Instant.parse("2026-07-27T01:00:00Z"), KST);
        ReflectionTestUtils.setField(notifier, "clock", nextDay);
        notifier.checkAndNotify();

        verify(slackWebhookClient, times(2)).send(anyString());
    }

    @Test
    @DisplayName("Slack 전송이 실패하면 같은 날에도 다음 주기에 재시도한다")
    void retriesWithinSameDayWhenSendFails() {
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(clock), List.of(summary("odsay", 1000L, 100L))));
        when(slackWebhookClient.send(anyString())).thenReturn(false);

        notifier.checkAndNotify();
        notifier.checkAndNotify();

        verify(slackWebhookClient, times(2)).send(anyString());
    }
}
