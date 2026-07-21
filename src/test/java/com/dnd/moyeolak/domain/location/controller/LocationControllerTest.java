package com.dnd.moyeolak.domain.location.controller;

import com.dnd.moyeolak.domain.location.dto.CenterPointDto;
import com.dnd.moyeolak.domain.location.dto.MidpointRecommendationResponse;
import com.dnd.moyeolak.domain.location.service.LocationVoteService;
import com.dnd.moyeolak.domain.location.service.MidpointRecommendationService;
import com.dnd.moyeolak.domain.location.service.NearbyPlaceSearchService;
import com.dnd.moyeolak.domain.location.service.PersonalRouteQueryService;
import com.dnd.moyeolak.global.exception.GlobalExceptionAdvice;
import com.dnd.moyeolak.global.ratelimit.ClientIpResolver;
import com.dnd.moyeolak.global.response.SuccessCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    private static final String MEETING_ID = "meeting-123";

    @Mock
    private LocationVoteService locationVoteService;

    @Mock
    private MidpointRecommendationService midpointRecommendationService;

    @Mock
    private PersonalRouteQueryService personalRouteQueryService;

    @Mock
    private NearbyPlaceSearchService nearbyPlaceSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocationController controller = new LocationController(
                locationVoteService,
                midpointRecommendationService,
                personalRouteQueryService,
                nearbyPlaceSearchService,
                new ClientIpResolver()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("중간지점 추천 API는 X-Forwarded-For 첫 번째 IP를 서비스에 전달한다")
    void getMidpointRecommendations_passesResolvedClientIp() throws Exception {
        MidpointRecommendationResponse response = new MidpointRecommendationResponse(
                new CenterPointDto(37.5, 127.0),
                List.of(),
                null,
                2,
                5
        );
        when(midpointRecommendationService.calculateMidpointRecommendations(
                eq(MEETING_ID), eq(null), eq("203.0.113.10")))
                .thenReturn(response);

        mockMvc.perform(get("/api/locations/midpoint-recommendations")
                        .param("meetingId", MEETING_ID)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SuccessCode.OK.getCode()))
                .andExpect(jsonPath("$.data.registeredCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(5));

        verify(midpointRecommendationService)
                .calculateMidpointRecommendations(MEETING_ID, null, "203.0.113.10");
    }
}
