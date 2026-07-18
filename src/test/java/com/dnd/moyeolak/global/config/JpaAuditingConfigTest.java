package com.dnd.moyeolak.global.config;

import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JpaAuditingConfigTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    @Qualifier("kstDateTimeProvider")
    private DateTimeProvider dateTimeProvider;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldProvideKoreanLocalDateTimeEvenWhenJvmDefaultTimezoneIsUtc() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        try {
            LocalDateTime providedNow = LocalDateTime.from(dateTimeProvider.getNow().orElseThrow());
            LocalDateTime kstNow = LocalDateTime.now(KST);

            assertThat(Duration.between(providedNow, kstNow).abs()).isLessThan(Duration.ofSeconds(2));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    @Transactional
    void shouldPersistAuditTimestampAsKoreanLocalDateTimeEvenWhenJvmDefaultTimezoneIsUtc() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        try {
            Meeting meeting = Meeting.of(3);

            Meeting saved = meetingRepository.save(meeting);
            entityManager.flush();
            entityManager.clear();

            Meeting found = meetingRepository.findById(saved.getId()).orElseThrow();
            LocalDateTime kstNow = LocalDateTime.now(KST);

            assertThat(Duration.between(found.getCreatedAt(), kstNow).abs()).isLessThan(Duration.ofSeconds(2));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }
}
