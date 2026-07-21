package com.dnd.moyeolak.global.ratelimit;

import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MidpointRecommendationUsageLimiter {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAILY_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MINUTE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final RateLimitProperties properties;
    private final RateLimitCounter counter;
    private final Clock clock;

    public void checkAndIncrease(String meetingId, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KST);
        String safeClientIp = normalizeClientIp(clientIp);
        List<RateLimitRule> rules = List.of(
                new RateLimitRule(
                        "midpoint:meeting:daily:%s:%s".formatted(dailyKey(now), meetingId),
                        nextMidnight(now),
                        properties.getMeetingDailyLimit()
                ),
                new RateLimitRule(
                        "midpoint:ip:minute:%s:%s".formatted(minuteKey(now), safeClientIp),
                        now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(2).toInstant(),
                        properties.getIpMinuteLimit()
                ),
                new RateLimitRule(
                        "midpoint:ip:daily:%s:%s".formatted(dailyKey(now), safeClientIp),
                        nextMidnight(now),
                        properties.getIpDailyLimit()
                )
        );

        for (RateLimitRule rule : rules) {
            long count = counter.increaseAndGet(rule.key(), rule.expiresAt());
            if (count > rule.limit()) {
                throw new BusinessException(ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED);
            }
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private String dailyKey(ZonedDateTime now) {
        return now.format(DAILY_KEY_FORMATTER);
    }

    private String minuteKey(ZonedDateTime now) {
        return now.format(MINUTE_KEY_FORMATTER);
    }

    private Instant nextMidnight(ZonedDateTime now) {
        return now.toLocalDate().plusDays(1).atStartOfDay(KST).toInstant();
    }

    private record RateLimitRule(String key, Instant expiresAt, int limit) {
    }
}
