package com.dnd.moyeolak.global.client.aws;

import com.dnd.moyeolak.global.client.aws.dto.AwsCostSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cost Explorer의 RECORD_TYPE=Credit 레코드를 읽어 크레딧 차감 현황을 만든다.
 * 크레딧 잔액 자체를 주는 API는 없으므로, 차감액 누적치만 여기서 계산하고
 * 잔액은 설정된 부여 총액에서 빼는 방식으로 {@link com.dnd.moyeolak.global.metrics.alert.AwsCreditNotifier}가 구한다.
 */
@Component
@ConditionalOnProperty(name = "aws.cost.enabled", havingValue = "true")
public class AwsCostExplorerClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String METRIC = "UnblendedCost";
    private static final int DAILY_AVERAGE_WINDOW_DAYS = 7;

    private final CostExplorerClient costExplorerClient;

    public AwsCostExplorerClient(CostExplorerClient costExplorerClient) {
        this.costExplorerClient = costExplorerClient;
    }

    public AwsCostSnapshot fetch(LocalDate creditStartDate, LocalDate today) {
        // Cost Explorer의 End는 배타적이라 오늘까지 포함하려면 +1일
        List<ResultByTime> monthly = query(Granularity.MONTHLY, creditStartDate, today.plusDays(1));
        // 당일 데이터는 부분 집계라 일평균을 왜곡하므로 오늘 직전 7일만 본다
        List<ResultByTime> daily = query(Granularity.DAILY, today.minusDays(DAILY_AVERAGE_WINDOW_DAYS), today);

        return new AwsCostSnapshot(
                sum(monthly),
                monthly.isEmpty() ? BigDecimal.ZERO : amountOf(monthly.getLast()),
                average(daily),
                today.minusDays(1));
    }

    private List<ResultByTime> query(Granularity granularity, LocalDate start, LocalDate end) {
        GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder()
                        .start(start.format(DATE))
                        .end(end.format(DATE))
                        .build())
                .granularity(granularity)
                .metrics(METRIC)
                .filter(Expression.builder()
                        .dimensions(DimensionValues.builder()
                                .key(Dimension.RECORD_TYPE)
                                .values("Credit")
                                .build())
                        .build())
                .build();
        return costExplorerClient.getCostAndUsage(request).resultsByTime();
    }

    /** 크레딧은 음수로 내려오므로 절대값으로 뒤집어 "사용액"으로 다룬다. */
    private BigDecimal amountOf(ResultByTime bucket) {
        MetricValue value = bucket.total().get(METRIC);
        if (value == null || value.amount() == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.amount()).abs();
    }

    private BigDecimal sum(List<ResultByTime> buckets) {
        return buckets.stream().map(this::amountOf).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal average(List<ResultByTime> buckets) {
        if (buckets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum(buckets).divide(BigDecimal.valueOf(buckets.size()), MathContext.DECIMAL64);
    }
}
