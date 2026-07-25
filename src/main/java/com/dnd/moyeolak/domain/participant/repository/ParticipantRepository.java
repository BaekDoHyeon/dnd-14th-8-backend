package com.dnd.moyeolak.domain.participant.repository;

import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByMeetingAndLocalStorageKey(Meeting meeting, String localStorageKey);

    Optional<Participant> findByMeetingIdAndLocalStorageKey(String meetingId, String localStorageKey);

    @Query("""
        SELECT DISTINCT p FROM Participant p
        JOIN FETCH p.meeting m
        LEFT JOIN FETCH m.participants participants
        WHERE p.localStorageKey = :localStorageKey
        ORDER BY m.createdAt DESC
    """)
    List<Participant> findAllByLocalStorageKeyWithMeetingAndParticipants(
            @Param("localStorageKey") String localStorageKey
    );
}
