package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.*;
import com.dnd.moyeolak.domain.location.entity.LocationVote;
import com.dnd.moyeolak.domain.location.enums.RouteMode;
import com.dnd.moyeolak.domain.location.repository.LocationVoteRepository;
import com.dnd.moyeolak.domain.location.service.PersonalRouteQueryService;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.domain.participant.entity.Participant;
import com.dnd.moyeolak.domain.participant.service.ParticipantService;
import com.dnd.moyeolak.global.client.kakao.KakaoDirectionsClient;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoDirectionsResponse;
import com.dnd.moyeolak.global.client.odsay.OdsayClient;
import com.dnd.moyeolak.global.client.odsay.dto.OdsayPathInfo;
import com.dnd.moyeolak.global.exception.BusinessException;
import com.dnd.moyeolak.global.response.ErrorCode;
import com.dnd.moyeolak.global.station.entity.Station;
import com.dnd.moyeolak.global.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PersonalRouteQueryServiceImpl implements PersonalRouteQueryService {

    private final MeetingService meetingService;
    private final ParticipantService participantService;
    private final LocationVoteRepository locationVoteRepository;
    private final StationRepository stationRepository;
    private final OdsayClient odsayClient;
    private final KakaoDirectionsClient kakaoDirectionsClient;

    @Override
    public PersonalRouteResponse getPersonalRoute(
            String meetingId,
            Long stationId,
            Long participantId,
            LocalDateTime departureTime,
            RouteMode mode
    ) {
        Meeting meeting = meetingService.get(meetingId);
        Participant participant = participantService.getById(participantId);
        if (participant.getMeeting() == null || !meeting.getId().equals(participant.getMeeting().getId())) {
            throw new BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND);
        }

        LocationVote locationVote = locationVoteRepository
                .findFirstByParticipant_IdOrderByCreatedAtDesc(participantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOCATION_VOTE_NOT_FOUND));

        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STATION_NOT_FOUND));

        TransitRouteDetailDto transit = null;
        if (mode.includeTransit()) {
            transit = buildTransitRoute(locationVote, station);
        }

        DrivingRouteDetailDto driving = null;
        if (mode.includeDriving()) {
            driving = buildDrivingRoute(locationVote, station, departureTime);
        }

        return new PersonalRouteResponse(
                new ParticipantSummaryDto(
                        participant.getId(),
                        participant.getName(),
                        locationVote.getDepartureLocation()
                ),
                new StationSummaryDto(
                        station.getId(),
                        station.getName(),
                        station.getLine()
                ),
                departureTime,
                transit,
                driving
        );
    }

    private TransitRouteDetailDto buildTransitRoute(LocationVote vote, Station station) {
        OdsayPathInfo pathInfo = odsayClient.searchRoute(
                vote.getDepartureLat().doubleValue(),
                vote.getDepartureLng().doubleValue(),
                station.getLatitude(),
                station.getLongitude()
        );

        if (pathInfo == null || pathInfo.safeTotal() >= 999) {
            throw new BusinessException(ErrorCode.ODSAY_API_ERROR);
        }

        int transferCount = pathInfo.safeBusTransit() + pathInfo.safeSubwayTransit();

        return new TransitRouteDetailDto(
                pathInfo.safeTotal(),
                pathInfo.safeTotalDistance(),
                pathInfo.safePayment(),
                transferCount,
                pathInfo.safeTotalWalk()
        );
    }

    private DrivingRouteDetailDto buildDrivingRoute(
            LocationVote vote,
            Station station,
            LocalDateTime departureTime
    ) {
        KakaoDirectionsResponse.Summary summary = kakaoDirectionsClient.requestDrivingRoute(
                vote.getDepartureLat().doubleValue(),
                vote.getDepartureLng().doubleValue(),
                station.getLatitude(),
                station.getLongitude(),
                departureTime
        );

        if (summary == null) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }

        int durationMinutes = summary.duration() / 60;
        int distanceMeters = summary.distance();
        int tollFare = summary.fare() != null ? summary.fare().safeToll() : 0;
        int taxiFare = summary.fare() != null ? summary.fare().safeTaxi() : 0;

        return new DrivingRouteDetailDto(durationMinutes, distanceMeters, tollFare, taxiFare);
    }
}
