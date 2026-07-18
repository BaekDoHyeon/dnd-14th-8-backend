package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.CreateLocationVoteRequest;
import com.dnd.moyeolak.domain.location.dto.LocationVoteResponse;
import com.dnd.moyeolak.domain.location.entity.LocationPoll;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.LocationVoteService;
import com.dnd.moyeolak.domain.meeting.dto.UpdateLocationVoteRequest;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.repository.MeetingRepository;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.service.ParticipantService;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationVoteServiceImpl implements LocationVoteService {

    private final MeetingRepository meetingRepository;
    private final ParticipantService participantService;
    private final LocationVoteRepository locationVoteRepository;

    // 수도권 전철망 커버리지 기준 서비스 지역 (남: 신창 36.77, 북: 소요산 37.95, 서: 인천 126.45, 동: 춘천 127.73 + 여유분)
    private static final double SERVICE_AREA_MIN_LAT = 36.5;
    private static final double SERVICE_AREA_MAX_LAT = 38.2;
    private static final double SERVICE_AREA_MIN_LNG = 126.2;
    private static final double SERVICE_AREA_MAX_LNG = 128.0;

    @Override
    public List<LocationVoteResponse> listLocationVote(String meetingId) {
        Meeting meeting = meetingRepository.findByIdWithAllAssociations(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        LocationPoll locationPoll = meeting.getLocationPoll();
        if (locationPoll == null) {
            throw new BusinessException(ErrorCode.LOCATION_POLL_NOT_FOUND);
        }

        return locationVoteRepository.findByLocationPoll_Id(locationPoll.getId())
                .stream()
                .map(LocationVoteResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "midpointRecommendations", allEntries = true)
    public void updateLocationVote(Long locationVoteId, UpdateLocationVoteRequest request) {
        validateServiceArea(request.departureLat(), request.departureLng());

        LocationVote locationVote = locationVoteRepository.findById(locationVoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOCATION_VOTE_NOT_FOUND));
        locationVote.update(request);
    }

    @Override
    @Transactional
    @CacheEvict(value = "midpointRecommendations", allEntries = true)
    public Long createLocationVote(CreateLocationVoteRequest request) {
        validateServiceArea(request.departureLat(), request.departureLng());

        Meeting meeting = meetingRepository.findByIdWithAllAssociations(request.meetingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        LocationPoll locationPoll = meeting.getLocationPoll();
        LocationVote locationVote = LocationVote.fromByCreateLocationVoteRequest(locationPoll, request);

        if (request.participantId() != null) {
            Participant participant = meeting.getParticipants().stream()
                    .filter(p -> request.participantId().equals(p.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND));

            if (!participant.getLocationVotes().isEmpty()) {
                throw new BusinessException(ErrorCode.DUPLICATE_LOCATION_VOTE);
            }
            participant.addLocationVote(locationVote);
            locationVoteRepository.save(locationVote);
            return locationVote.getId();
        }

        if (!StringUtils.hasText(request.localStorageKey())) {
            locationVoteRepository.save(locationVote);
            return locationVote.getId();
        }

        Optional<Participant> existingParticipant = meeting.getParticipants().stream()
                .filter(p -> request.localStorageKey().equals(p.getLocalStorageKey()))
                .findFirst();

        if (existingParticipant.isPresent()) {
            Participant participant = existingParticipant.get();
            if (!participant.getLocationVotes().isEmpty()) {
                throw new BusinessException(ErrorCode.DUPLICATE_LOCAL_STORAGE_KEY);
            }
            participant.addLocationVote(locationVote);
            locationVoteRepository.save(locationVote);
            return locationVote.getId();
        }

        Participant participant = Participant.of(
                Meeting.ofId(request.meetingId()), request.localStorageKey(), request.participantName(), locationVote
        );
        participantService.save(participant);

        return locationVote.getId();
    }

    private void validateServiceArea(String departureLat, String departureLng) {
        double lat;
        double lng;
        try {
            lat = Double.parseDouble(departureLat);
            lng = Double.parseDouble(departureLng);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        if (lat < SERVICE_AREA_MIN_LAT || lat > SERVICE_AREA_MAX_LAT
                || lng < SERVICE_AREA_MIN_LNG || lng > SERVICE_AREA_MAX_LNG) {
            throw new BusinessException(ErrorCode.OUT_OF_SERVICE_AREA);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "midpointRecommendations", allEntries = true)
    public void deleteLocationVote(Long locationVoteId) {
        LocationVote locationVote = locationVoteRepository.findById(locationVoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOCATION_VOTE_NOT_FOUND));
        locationVoteRepository.delete(locationVote);
    }
}
