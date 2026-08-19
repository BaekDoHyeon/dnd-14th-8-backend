package com.dnd.moyeolak.global.metrics.alert;

import com.dnd.moyeolak.global.client.aws.AwsCostExplorerClient;
import com.dnd.moyeolak.global.client.aws.dto.AwsCostSnapshot;
import com.dnd.moyeolak.global.client.slack.SlackWebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AwsCreditNotifierTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), KST);
    private static final String CREDIT_START = "2026-07-01";

    private AwsCostExplorerClient costExplorerClient;
    private SlackWebhookClient slackWebhookClient;

    @BeforeEach
    void setUp() {
        costExplorerClient = mock(AwsCostExplorerClient.class);
        slackWebhookClient = mock(SlackWebhookClient.class);
        when(slackWebhookClient.send(anyString())).thenReturn(true);
    }

    private AwsCreditNotifier notifier(String creditTotal) {
        return new AwsCreditNotifier(costExplorerClient, slackWebhookClient, CLOCK, creditTotal, CREDIT_START);
    }

    private void givenSnapshot(String totalUsed, String monthToDate, String dailyAverage) {
        when(costExplorerClient.fetch(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new AwsCostSnapshot(
                        new BigDecimal(totalUsed),
                        new BigDecimal(monthToDate),
                        new BigDecimal(dailyAverage),
                        LocalDate.of(2026, 8, 18)));
    }

    private String captureSentMessage() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).send(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("크레딧 총액이 설정되면 잔액과 소진 예상일을 메시지에 포함한다")
    void includesRemainingCreditAndDepletionDateWhenTotalConfigured() {
        givenSnapshot("9.2261", "3.7895", "0.2090");

        notifier("300").sendCreditStatus();

        String message = captureSentMessage();
        assertThat(message).contains("$290.77");          // 300 - 9.2261
        assertThat(message).contains("$300.00");
        assertThat(message).contains("96.9%");            // 290.7739 / 300
        assertThat(message).contains("$9.23");            // 누적 사용
        assertThat(message).contains("$3.79");            // 이번 달 사용
        assertThat(message).contains("$0.21");            // 일평균
        assertThat(message).contains("2026-08-18");       // 데이터 기준일
        assertThat(message).contains("소진 예상");
        assertThat(message).contains("1391일");           // 290.7739 / 0.2090 내림
    }

    @Test
    @DisplayName("크레딧 총액이 없으면 잔액 없이 누적 사용액만 전송한다")
    void sendsUsageOnlyWhenCreditTotalMissing() {
        givenSnapshot("9.2261", "3.7895", "0.2090");

        notifier("").sendCreditStatus();

        String message = captureSentMessage();
        assertThat(message).contains("$9.23");
        assertThat(message).contains("$3.79");
        assertThat(message).doesNotContain("남은 크레딧");
        assertThat(message).doesNotContain("소진 예상");
        assertThat(message).contains("AWS_CREDIT_TOTAL");
    }

    @Test
    @DisplayName("일평균 사용액이 0이면 소진 예상일을 생략한다")
    void omitsDepletionDateWhenDailyAverageIsZero() {
        givenSnapshot("9.2261", "0", "0");

        notifier("300").sendCreditStatus();

        String message = captureSentMessage();
        assertThat(message).contains("남은 크레딧");
        assertThat(message).doesNotContain("소진 예상");
    }

    @Test
    @DisplayName("잔액이 이미 소진되면 남은 크레딧을 0으로 보고한다")
    void reportsZeroWhenCreditsExhausted() {
        givenSnapshot("320.5", "40.0", "1.0");

        notifier("300").sendCreditStatus();

        String message = captureSentMessage();
        assertThat(message).contains("$0.00");
        assertThat(message).doesNotContain("소진 예상");
    }

    @Test
    @DisplayName("일평균이 극히 작아 소진일이 아득하면 날짜 대신 상한 표현을 쓴다")
    void capsDepletionForecastWhenDailyAverageIsNegligible() {
        givenSnapshot("9.2261", "0.0000001", "0.0000001");   // 하루 $0.0000001 -> 약 80억 일

        notifier("300").sendCreditStatus();

        String message = captureSentMessage();
        assertThat(message).contains("10년 이상");
        assertThat(message).doesNotContain("일 남음");
    }

    @Test
    @DisplayName("Cost Explorer 조회에 실패하면 Slack으로 전송하지 않는다")
    void doesNotSendWhenCostExplorerFails() {
        when(costExplorerClient.fetch(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new RuntimeException("AccessDeniedException"));

        notifier("300").sendCreditStatus();

        verify(slackWebhookClient, never()).send(anyString());
    }

    @Test
    @DisplayName("크레딧 시작일과 오늘 날짜로 Cost Explorer를 조회한다")
    void queriesCostExplorerWithConfiguredRange() {
        givenSnapshot("9.2261", "3.7895", "0.2090");

        notifier("300").sendCreditStatus();

        verify(costExplorerClient).fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 19));
    }
}
