package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import com.dnd.moyeolak.global.metrics.stats.ExternalApiStatsService;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 매일 지정 시각에 오늘 생성된 모임 수와 외부 API 호출 통계를 Slack으로 요약 전송한다.
 * 별도 계측 없이 기존 MeetingService/ExternalApiStatsService의 계산 결과를 그대로 읽는다.
 */
@Component
public class DailySummaryNotifier {

    private final MeetingService meetingService;
    private final ExternalApiStatsService statsService;
    private final SlackWebhookClient slackWebhookClient;
    private final Clock clock;

    public DailySummaryNotifier(
            MeetingService meetingService,
            ExternalApiStatsService statsService,
            SlackWebhookClient slackWebhookClient,
            Clock clock
    ) {
        this.meetingService = meetingService;
        this.statsService = statsService;
        this.slackWebhookClient = slackWebhookClient;
        this.clock = clock;
    }

    @Scheduled(cron = "${external-api.alert.daily-summary-cron:0 0 23 * * *}")
    public void sendDailySummary() {
        long todayMeetingCount = meetingService.getLandingStats().todayCreatedMeetingCount();
        List<ApiSummary> apis = statsService.summary().apis();
        slackWebhookClient.send(buildMessage(todayMeetingCount, apis));
    }

    private String buildMessage(long todayMeetingCount, List<ApiSummary> apis) {
        String date = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE);
        StringBuilder sb = new StringBuilder();
        sb.append(":bar_chart: 오늘의 서비스 통계 (").append(date).append(")\n\n");
        sb.append("오늘 생성된 모임: ").append(todayMeetingCount).append("건\n\n");
        sb.append("외부 API 호출 현황\n");
        for (ApiSummary api : apis) {
            sb.append(apiLine(api)).append('\n');
        }
        return sb.toString();
    }

    private String apiLine(ApiSummary api) {
        if (api.dailyQuota() == null) {
            String cost = api.todayCostUsd() == null ? "" : " 예상 비용 $%.2f".formatted(api.todayCostUsd());
            return "• %s: %d회 호출,%s".formatted(api.api(), api.calls(), cost);
        }
        double usedPercent = api.dailyQuota() == 0 ? 0.0 : api.calls() * 100.0 / api.dailyQuota();
        return "• %s: %d/%d 호출 (%.1f%%), 성공률 %.1f%%".formatted(
                api.api(), api.calls(), api.dailyQuota(), usedPercent, api.successRate());
    }
}
