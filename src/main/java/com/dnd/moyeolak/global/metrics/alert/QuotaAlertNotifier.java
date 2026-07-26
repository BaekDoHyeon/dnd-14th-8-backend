package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import com.dnd.moyeolak.global.metrics.stats.ExternalApiStatsService;
import com.dnd.moyeolak.global.metrics.stats.dto.ExternalApiSummaryResponse.ApiSummary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 외부 API 일일 quota 잔여 비율이 임계치 미만이면 Slack으로 알림한다.
 * quota가 없는 API(Google Routes 등 종량제)는 대상에서 제외한다.
 */
@Component
public class QuotaAlertNotifier {

    private final ExternalApiStatsService statsService;
    private final SlackWebhookClient slackWebhookClient;
    private final ExternalApiAlertProperties properties;
    private final Clock clock;
    private final Map<String, LocalDate> lastAlertedDate = new ConcurrentHashMap<>();

    public QuotaAlertNotifier(
            ExternalApiStatsService statsService,
            SlackWebhookClient slackWebhookClient,
            ExternalApiAlertProperties properties,
            Clock clock
    ) {
        this.statsService = statsService;
        this.slackWebhookClient = slackWebhookClient;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkAndNotify() {
        LocalDate today = LocalDate.now(clock);
        for (ApiSummary summary : statsService.summary().apis()) {
            if (summary.dailyQuota() == null || summary.dailyQuota() == 0) {
                continue;
            }
            double remainingPercent = summary.quotaRemaining() * 100.0 / summary.dailyQuota();
            if (remainingPercent >= properties.getThresholdPercent()) {
                continue;
            }
            LocalDate previousAlertDate = lastAlertedDate.put(summary.api(), today);
            if (today.equals(previousAlertDate)) {
                continue; // 오늘 이미 알림을 보냈거나(또는 경쟁 스레드가 방금 기록함) - put은 어느 쪽이든 오늘 날짜로 기록됨
            }
            boolean sent = slackWebhookClient.send(alertMessage(summary, remainingPercent));
            if (!sent) {
                lastAlertedDate.remove(summary.api());
            }
        }
    }

    private String alertMessage(ApiSummary summary, double remainingPercent) {
        return "[quota 경고] %s 일일 호출 한도 잔여 %.1f%% (남은 %d / %d회)".formatted(
                summary.api(), remainingPercent, summary.quotaRemaining(), summary.dailyQuota());
    }
}
