package com.dnd.moyeolak.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "moyeolak.rate-limit.midpoint-recommendation")
public class RateLimitProperties {

    private boolean enabled = true;
    private int meetingDailyLimit = 20;
    private int ipMinuteLimit = 5;
    private int ipDailyLimit = 100;
}
