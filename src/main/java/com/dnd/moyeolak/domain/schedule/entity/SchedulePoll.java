package com.dnd.moyeolak.domain.schedule.entity;

import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.global.converter.LocalDateListConverter;
import com.dnd.moyeolak.global.entity.BaseEntity;
import com.dnd.moyeolak.global.enums.PollStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulePoll extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_poll_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Builder.Default
    @Convert(converter = LocalDateListConverter.class)
    @Column(length = 1000, comment = "날짜 범위", nullable = false)
    private List<LocalDate> dateOptions = new ArrayList<>();

    @Builder.Default
    @Column(name = "start_time", comment = "시작 시간(분 단위)", nullable = false)
    @Getter(AccessLevel.NONE)
    private int startTime = 9 * 60;

    @Builder.Default
    @Column(name = "end_time", comment = "종료 시간(분 단위)", nullable = false)
    @Getter(AccessLevel.NONE)
    private int endTime = 24 * 60;

    @Column(comment = "확정 시작 시간")
    private LocalDateTime confirmedStartTime;

    @Column(comment = "확정 종료 시간")
    private LocalDateTime confirmedEndTime;

    @Builder.Default
    @Column(comment = "투표 상태", nullable = false)
    @Enumerated(EnumType.STRING)
    private PollStatus pollStatus = PollStatus.INACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "schedulePoll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleVote> scheduleVotes = new ArrayList<>();

    public static SchedulePoll defaultOf(Meeting meeting) {
        LocalDate nowDate = LocalDate.now();
        List<LocalDate> defaultDates = nowDate
                .datesUntil(nowDate.plusDays(14))
                .collect(Collectors.toCollection(ArrayList::new));

        return SchedulePoll.builder()
                .meeting(meeting)
                .dateOptions(defaultDates)
                .build();
    }

    public void updateOptions(List<LocalDate> dateOptions, int startTime, int endTime) {
        this.dateOptions.clear();
        this.dateOptions.addAll(dateOptions);
        this.dateOptions.sort(Comparator.naturalOrder());
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void updateStatus(PollStatus pollStatus) {
        this.pollStatus = pollStatus;
    }

    public int getStartTime() {
        return normalizeMinuteOfDay(startTime);
    }

    public int getEndTime() {
        return normalizeMinuteOfDay(endTime);
    }

    private int normalizeMinuteOfDay(int rawValue) {
        // 기존 데이터(0~24)는 시간을 의미하므로 분 단위로 환산해 저장된 값과 호환
        if (rawValue <= 24) {
            return rawValue * 60;
        }
        return rawValue;
    }
}
