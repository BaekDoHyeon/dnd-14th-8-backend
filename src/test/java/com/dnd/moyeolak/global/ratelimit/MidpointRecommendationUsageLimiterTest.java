package com.dnd.moyeolak.global.ratelimit;

import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MidpointRecommendationUsageLimiterTest {

    private RateLimitProperties properties;
    private MidpointRecommendationUsageLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setMeetingDailyLimit(2);
        properties.setIpMinuteLimit(2);
        properties.setIpDailyLimit(3);

        Clock clock = Clock.fixed(Instant.parse("2026-07-22T03:00:00Z"), ZoneId.of("UTC"));
        limiter = new MidpointRecommendationUsageLimiter(
                properties,
                new InMemoryRateLimitCounter(clock),
                clock
        );
    }

    @Test
    @DisplayName("meeting 일별 제한을 초과하면 429 예외가 발생한다")
    void checkAndIncrease_blocksWhenMeetingDailyLimitExceeded() {
        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
        limiter.checkAndIncrease("meeting-1", "203.0.113.11");

        assertThatThrownBy(() -> limiter.checkAndIncrease("meeting-1", "203.0.113.12"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("IP 분당 제한을 초과하면 429 예외가 발생한다")
    void checkAndIncrease_blocksWhenIpMinuteLimitExceeded() {
        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
        limiter.checkAndIncrease("meeting-2", "203.0.113.10");

        assertThatThrownBy(() -> limiter.checkAndIncrease("meeting-3", "203.0.113.10"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("IP 일별 제한을 초과하면 429 예외가 발생한다")
    void checkAndIncrease_blocksWhenIpDailyLimitExceeded() {
        properties.setIpMinuteLimit(10);
        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
        limiter.checkAndIncrease("meeting-2", "203.0.113.10");
        limiter.checkAndIncrease("meeting-3", "203.0.113.10");

        assertThatThrownBy(() -> limiter.checkAndIncrease("meeting-4", "203.0.113.10"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ErrorCode.MIDPOINT_RECOMMENDATION_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("비활성화 상태이면 제한을 적용하지 않는다")
    void checkAndIncrease_allowsWhenDisabled() {
        properties.setEnabled(false);

        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
        limiter.checkAndIncrease("meeting-1", "203.0.113.10");
    }
}
