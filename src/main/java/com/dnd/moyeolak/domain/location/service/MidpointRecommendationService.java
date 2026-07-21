package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;

import java.time.LocalDateTime;

public interface MidpointRecommendationService {

    MidpointRecommendationResponse calculateMidpointRecommendations(
            String meetingId,
            LocalDateTime departureTime,
            String clientIp
    );
}
