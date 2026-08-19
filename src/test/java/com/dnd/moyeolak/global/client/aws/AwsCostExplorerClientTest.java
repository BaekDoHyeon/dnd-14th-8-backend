package com.dnd.moyeolak.global.client.aws;

import com.dnd.moyeolak.global.client.aws.dto.AwsCostSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsCostExplorerClientTest {

    private static final LocalDate CREDIT_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private CostExplorerClient sdk;
    private AwsCostExplorerClient client;

    @BeforeEach
    void setUp() {
        sdk = mock(CostExplorerClient.class);
        client = new AwsCostExplorerClient(sdk);
    }

    private static ResultByTime bucket(String start, String end, String amount) {
        return ResultByTime.builder()
                .timePeriod(DateInterval.builder().start(start).end(end).build())
                .total(Map.of("UnblendedCost", MetricValue.builder().amount(amount).unit("USD").build()))
                .build();
    }

    private static GetCostAndUsageResponse response(ResultByTime... buckets) {
        return GetCostAndUsageResponse.builder().resultsByTime(List.of(buckets)).build();
    }

    /** MONTHLY 요청과 DAILY 요청에 서로 다른 응답을 돌려준다. */
    private void stubSdk(GetCostAndUsageResponse monthly, GetCostAndUsageResponse daily) {
        when(sdk.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenAnswer(invocation -> {
            GetCostAndUsageRequest request = invocation.getArgument(0);
            return request.granularity() == Granularity.MONTHLY ? monthly : daily;
        });
    }

    @Test
    @DisplayName("음수로 내려오는 월별 크레딧을 절대값으로 합산해 누적 사용액을 만든다")
    void sumsMonthlyCreditsAsPositiveTotal() {
        stubSdk(
                response(
                        bucket("2026-07-01", "2026-08-01", "-5.4366630976"),
                        bucket("2026-08-01", "2026-08-20", "-3.7894508568")),
                response(bucket("2026-08-18", "2026-08-19", "-0.2089961591")));

        AwsCostSnapshot snapshot = client.fetch(CREDIT_START, TODAY);

        assertThat(snapshot.totalCreditsUsed().doubleValue()).isCloseTo(9.2261139544, within(0.0000001));
        assertThat(snapshot.monthToDateCreditsUsed().doubleValue()).isCloseTo(3.7894508568, within(0.0000001));
    }

    @Test
    @DisplayName("최근 일별 크레딧 사용액의 평균을 일평균으로 계산한다")
    void averagesRecentDailyCredits() {
        stubSdk(
                response(bucket("2026-08-01", "2026-08-20", "-3.0")),
                response(
                        bucket("2026-08-16", "2026-08-17", "-0.10"),
                        bucket("2026-08-17", "2026-08-18", "-0.20"),
                        bucket("2026-08-18", "2026-08-19", "-0.30")));

        AwsCostSnapshot snapshot = client.fetch(CREDIT_START, TODAY);

        assertThat(snapshot.recentDailyAverage().doubleValue()).isCloseTo(0.20, within(0.0000001));
    }

    @Test
    @DisplayName("데이터 기준일은 어제다 - Cost Explorer는 당일 데이터가 불완전하다")
    void reportsYesterdayAsDataThrough() {
        stubSdk(response(bucket("2026-08-01", "2026-08-20", "-3.0")),
                response(bucket("2026-08-18", "2026-08-19", "-0.2")));

        AwsCostSnapshot snapshot = client.fetch(CREDIT_START, TODAY);

        assertThat(snapshot.dataThrough()).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("집계 구간에 데이터가 없으면 0으로 채운다")
    void fillsZeroWhenNoData() {
        stubSdk(response(), response());

        AwsCostSnapshot snapshot = client.fetch(CREDIT_START, TODAY);

        assertThat(snapshot.totalCreditsUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.monthToDateCreditsUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.recentDailyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("누적 조회는 크레딧 시작일부터 오늘까지, 일평균 조회는 오늘 직전 7일을 대상으로 한다")
    void queriesExpectedTimePeriods() {
        stubSdk(response(bucket("2026-08-01", "2026-08-20", "-3.0")),
                response(bucket("2026-08-18", "2026-08-19", "-0.2")));

        client.fetch(CREDIT_START, TODAY);

        ArgumentCaptor<GetCostAndUsageRequest> captor = ArgumentCaptor.forClass(GetCostAndUsageRequest.class);
        verify(sdk, times(2)).getCostAndUsage(captor.capture());

        GetCostAndUsageRequest monthly = captor.getAllValues().stream()
                .filter(r -> r.granularity() == Granularity.MONTHLY).findFirst().orElseThrow();
        assertThat(monthly.timePeriod().start()).isEqualTo("2026-07-01");
        assertThat(monthly.timePeriod().end()).isEqualTo("2026-08-20");   // End는 배타적이라 오늘 포함하려면 +1일
        assertThat(monthly.filter().dimensions().key()).isEqualTo(Dimension.RECORD_TYPE);
        assertThat(monthly.filter().dimensions().values()).containsExactly("Credit");

        GetCostAndUsageRequest daily = captor.getAllValues().stream()
                .filter(r -> r.granularity() == Granularity.DAILY).findFirst().orElseThrow();
        assertThat(daily.timePeriod().start()).isEqualTo("2026-08-12");
        assertThat(daily.timePeriod().end()).isEqualTo("2026-08-19");     // 오늘은 부분 데이터라 제외
    }
}
