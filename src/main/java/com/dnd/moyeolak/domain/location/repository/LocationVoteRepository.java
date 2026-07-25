package com.dnd.moyeolak.domain.location.repository;

import com.dnd.moyeolak.domain.location.entity.LocationVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationVoteRepository extends JpaRepository<LocationVote, Long> {

    // participant를 fetch join으로 함께 조회 — 지연 로딩으로 인해 트랜잭션 밖에서 접근 시
    // LazyInitializationException이 발생하는 것을 막는다 (중간지점 추천 계산에서 사용)
    @Query("SELECT lv FROM LocationVote lv LEFT JOIN FETCH lv.participant WHERE lv.locationPoll.id = :locationPollId")
    List<LocationVote> findByLocationPoll_Id(@Param("locationPollId") Long locationPollId);

    Optional<LocationVote> findFirstByParticipant_IdOrderByCreatedAtDesc(Long participantId);
}
