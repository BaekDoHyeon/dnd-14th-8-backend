package com.dnd.moyeolak.domain.meeting.controller;

import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.service.MeetingService;
import com.dnd.moyeolak.global.exception.GlobalExceptionAdvice;
import com.dnd.moyeolak.global.response.SuccessCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    @Mock
    private MeetingService meetingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MeetingController controller = new MeetingController(meetingService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("랜딩 통계 조회 API는 오늘 생성된 모임 수를 반환한다")
    void getLandingStats_returnsTodayCreatedMeetingCount() throws Exception {
        // given
        when(meetingService.getLandingStats()).thenReturn(new LandingStatsResponse(128L));

        // when & then
        mockMvc.perform(get("/api/meetings/stats"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SuccessCode.OK.getCode()))
                .andExpect(jsonPath("$.data.todayCreatedMeetingCount").value(128));

        verify(meetingService).getLandingStats();
    }
}
