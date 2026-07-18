package com.dnd.moyeolak.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaAuditingConfig {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(KST));
    }
}
