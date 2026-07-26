package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import com.dnd.moyeolak.global.metrics.stats.ExternalApiStatsService;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class DailySummaryNotifierTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T14:50:00Z"), KST);

    private MeetingService meetingService;
    private ExternalApiStatsService statsService;
    private SlackWebhookClient slackWebhookClient;
    private DailySummaryNotifier notifier;

    @BeforeEach
    void setUp() {
        meetingService = mock(MeetingService.class);
        statsService = mock(ExternalApiStatsService.class);
        slackWebhookClient = mock(SlackWebhookClient.class);
        when(slackWebhookClient.send(anyString())).thenReturn(true);
        notifier = new DailySummaryNotifier(meetingService, statsService, slackWebhookClient, CLOCK);
    }

    private ApiSummary quotaApi(String api, long calls, long dailyQuota, long quotaRemaining, double successRate) {
        return new ApiSummary(api, calls, calls, 0, successRate, 0, 0.0, dailyQuota, quotaRemaining, null);
    }

    private ApiSummary payPerUseApi(String api, long calls, double cost) {
        return new ApiSummary(api, calls, calls, 0, 100.0, 0, 0.0, null, null, cost);
    }

    @Test
    @DisplayName("오늘 생성된 모임 수와 API별 통계를 하나의 Slack 메시지로 전송한다")
    void sendsCombinedDailySummary() {
        when(meetingService.getLandingStats()).thenReturn(new LandingStatsResponse(12));
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(CLOCK),
                List.of(
                        quotaApi("odsay", 342, 1000, 658, 98.2),
                        payPerUseApi("google_routes", 130, 0.65)
                )));

        notifier.sendDailySummary();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).send(captor.capture());
        String message = captor.getValue();

        assertThat(message).contains("12건");
        assertThat(message).contains("odsay");
        assertThat(message).contains("342");
        assertThat(message).contains("1000");
        assertThat(message).contains("google_routes");
        assertThat(message).contains("0.65");
    }

    @Test
    @DisplayName("모임이 0건이어도 정상적으로 메시지를 보낸다")
    void sendsSummaryEvenWhenNoMeetingsToday() {
        when(meetingService.getLandingStats()).thenReturn(new LandingStatsResponse(0));
        when(statsService.summary()).thenReturn(new ExternalApiSummaryResponse(
                LocalDateTime.now(CLOCK), List.of()));

        notifier.sendDailySummary();

        verify(slackWebhookClient).send(contains("0건"));
    }
}
