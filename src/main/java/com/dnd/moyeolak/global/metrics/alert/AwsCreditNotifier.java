package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.global.client.aws.AwsCostExplorerClient;
import com.dnd.moyeolak.global.client.aws.dto.AwsCostSnapshot;
import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 매일 지정 시각에 AWS 크레딧 차감 현황을 Slack으로 전송한다.
 *
 * <p>AWS는 크레딧 잔액 조회 API를 제공하지 않는다. 그래서 부여받은 총액을 설정값
 * ({@code aws.cost.credit-total})으로 받아두고, Cost Explorer가 보고한 누적 차감액을 빼서 잔액을 낸다.
 * 총액이 없으면 잔액 없이 사용액만 보낸다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aws.cost.enabled", havingValue = "true")
public class AwsCreditNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 일평균이 0에 가까우면 소진일이 서기 79만년 같은 값으로 나온다. 그 위로는 날짜를 쓰지 않는다. */
    private static final BigDecimal MAX_FORECAST_DAYS = BigDecimal.valueOf(3650);

    private final AwsCostExplorerClient awsCostExplorerClient;
    private final SlackWebhookClient slackWebhookClient;
    private final Clock clock;
    private final BigDecimal creditTotal;
    private final LocalDate creditStartDate;

    public AwsCreditNotifier(
            AwsCostExplorerClient awsCostExplorerClient,
            SlackWebhookClient slackWebhookClient,
            Clock clock,
            @Value("${aws.cost.credit-total:}") String creditTotal,
            @Value("${aws.cost.credit-start-date:2026-07-01}") String creditStartDate
    ) {
        this.awsCostExplorerClient = awsCostExplorerClient;
        this.slackWebhookClient = slackWebhookClient;
        this.clock = clock;
        this.creditTotal = parseCreditTotal(creditTotal);
        this.creditStartDate = LocalDate.parse(creditStartDate.trim(), DATE);
    }

    @Scheduled(cron = "${aws.cost.cron:0 0 9 * * *}")
    public void sendCreditStatus() {
        LocalDate today = LocalDate.now(clock);
        AwsCostSnapshot snapshot;
        try {
            snapshot = awsCostExplorerClient.fetch(creditStartDate, today);
        } catch (Exception e) {
            // 요금 알림 실패가 애플리케이션에 영향을 주면 안 되므로 로그만 남기고 조용히 넘어간다
            log.error("AWS Cost Explorer 조회 실패 - 크레딧 알림 생략: {}", e.toString());
            return;
        }
        slackWebhookClient.send(buildMessage(snapshot));
    }

    private String buildMessage(AwsCostSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(":moneybag: AWS 크레딧 현황 (")
                .append(snapshot.dataThrough().format(DATE))
                .append(" 기준)\n\n");

        if (creditTotal != null) {
            appendBalance(sb, snapshot);
        }
        sb.append("누적 사용: ").append(usd(snapshot.totalCreditsUsed()))
                .append(" (").append(creditStartDate.format(DATE)).append(" 이후)\n");
        sb.append("이번 달 사용: ").append(usd(snapshot.monthToDateCreditsUsed())).append('\n');
        sb.append("최근 7일 일평균: ").append(usd(snapshot.recentDailyAverage())).append('\n');

        if (creditTotal != null) {
            appendDepletionForecast(sb, snapshot);
        } else {
            sb.append("\n크레딧 총액이 설정되지 않아 잔액을 계산하지 못했습니다. ")
                    .append("AWS_CREDIT_TOTAL 환경변수를 설정하세요.\n");
        }
        return sb.toString();
    }

    private void appendBalance(StringBuilder sb, AwsCostSnapshot snapshot) {
        BigDecimal remaining = remaining(snapshot);
        BigDecimal percent = creditTotal.signum() == 0
                ? BigDecimal.ZERO
                : remaining.multiply(BigDecimal.valueOf(100)).divide(creditTotal, MathContext.DECIMAL64);
        sb.append("남은 크레딧: ").append(usd(remaining)).append(" / ").append(usd(creditTotal))
                .append(" (").append(percent.setScale(1, RoundingMode.HALF_UP)).append("%)\n");
    }

    private void appendDepletionForecast(StringBuilder sb, AwsCostSnapshot snapshot) {
        BigDecimal remaining = remaining(snapshot);
        BigDecimal dailyAverage = snapshot.recentDailyAverage();
        if (dailyAverage.signum() <= 0 || remaining.signum() <= 0) {
            return;
        }
        BigDecimal daysLeft = remaining.divide(dailyAverage, MathContext.DECIMAL64)
                .setScale(0, RoundingMode.DOWN);
        if (daysLeft.compareTo(MAX_FORECAST_DAYS) > 0) {
            sb.append("소진 예상: 10년 이상 남음\n");
            return;
        }
        long days = daysLeft.longValueExact();
        sb.append("소진 예상: ").append(snapshot.dataThrough().plusDays(days).format(DATE))
                .append(" (").append(days).append("일 남음)\n");
    }

    private BigDecimal remaining(AwsCostSnapshot snapshot) {
        return creditTotal.subtract(snapshot.totalCreditsUsed()).max(BigDecimal.ZERO);
    }

    private String usd(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal parseCreditTotal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("aws.cost.credit-total 값을 숫자로 읽을 수 없어 잔액 계산을 생략합니다: {}", raw);
            return null;
        }
    }
}
