package com.dnd.moyeolak.domain.meeting.repository;

import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, String> {

    @Query("""
        SELECT m FROM Meeting m
        LEFT JOIN FETCH m.schedulePoll
        LEFT JOIN FETCH m.locationPoll
        LEFT JOIN FETCH m.participants
        WHERE m.id = :meetingId
    """)
    Optional<Meeting> findByIdWithAllAssociations(@Param("meetingId") String meetingId);

    @Query("""
        SELECT m.id FROM Meeting m
    """)
    List<String> findAllMeetingsId();

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
