package com.dnd.moyeolak.global.client.aws.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cost Explorer에서 읽어온 크레딧 차감 현황. 금액은 모두 USD 양수로 정규화한다.
 *
 * @param totalCreditsUsed        크레딧 시작일 이후 누적 차감액
 * @param monthToDateCreditsUsed  이번 달 차감액
 * @param recentDailyAverage      최근 7일 일평균 차감액
 * @param dataThrough             집계에 반영된 마지막 날짜 (Cost Explorer는 약 24h 지연)
 */
public record AwsCostSnapshot(
        BigDecimal totalCreditsUsed,
        BigDecimal monthToDateCreditsUsed,
        BigDecimal recentDailyAverage,
        LocalDate dataThrough
) {
}
