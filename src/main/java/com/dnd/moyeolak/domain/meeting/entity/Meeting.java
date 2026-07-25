package com.dnd.moyeolak.domain.meeting.entity;

import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.meeting.enums.MeetingFlow;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.schedule.entity.SchedulePoll;
import com.dnd.moyeolak.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting extends BaseEntity {

    @Id
    @Column(name = "meeting_id", length = 21, comment = "회의 ID")
    private String id;

    @Column(comment = "참여자 수")
    private int participantCount;

    @Builder.Default
    @Column(name = "initial_flow", nullable = false)
    @Enumerated(EnumType.STRING)
    private MeetingFlow initialFlow = MeetingFlow.SCHEDULE;

    @OneToOne(mappedBy = "meeting", cascade = CascadeType.ALL)
    private SchedulePoll schedulePoll;

    @OneToOne(mappedBy = "meeting", cascade = CascadeType.ALL)
    private LocationPoll locationPoll;

    @Builder.Default
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    // save() 호출 시 자동 실행되어 NanoId 생성
    @PrePersist
    public void generateMeetingId() {
        if (id == null) {
            id = NanoIdUtils.randomNanoId();
        }
    }

    public void addPolls(SchedulePoll schedulePoll, LocationPoll locationPoll) {
        this.schedulePoll = schedulePoll;
        this.locationPoll = locationPoll;
    }

    public void addParticipant(Participant participant) {
        participants.add(participant);
    }

    public void update(int participantCount) {
        this.participantCount = participantCount;
    }

    public static Meeting of(int participantCount) {
        return of(participantCount, MeetingFlow.SCHEDULE);
    }

    public static Meeting of(int participantCount, MeetingFlow initialFlow) {
        return Meeting.builder()
                .participantCount(participantCount)
                .initialFlow(initialFlow == null ? MeetingFlow.SCHEDULE : initialFlow)
                .build();
    }

    public static Meeting ofId(String id) {
        return Meeting.builder()
                .id(id)
                .initialFlow(MeetingFlow.SCHEDULE)
                .build();
    }
}
