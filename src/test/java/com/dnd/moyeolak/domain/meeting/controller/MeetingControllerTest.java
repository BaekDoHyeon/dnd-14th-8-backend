package com.dnd.moyeolak.domain.meeting.controller;

import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.dto.MyMeetingResponse;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    private static final String LOCAL_STORAGE_KEY = "local-storage-key";

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

    @Test
    @DisplayName("내 모임 목록 조회 API는 200 상태와 모임 목록을 반환한다")
    void getMyMeetings_returnsOkResponse() throws Exception {
        // given
        List<MyMeetingResponse> response = List.of(
                new MyMeetingResponse(
                        "meeting-id",
                        "민수",
                        4,
                        LocalDateTime.of(2026, 7, 23, 14, 10),
                        true
                )
        );
        when(meetingService.findMyMeetings(LOCAL_STORAGE_KEY)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/meetings/me")
                        .param("localStorageKey", LOCAL_STORAGE_KEY))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(SuccessCode.OK.getCode()))
                .andExpect(jsonPath("$.data[0].meetingId").value("meeting-id"))
                .andExpect(jsonPath("$.data[0].hostName").value("민수"))
                .andExpect(jsonPath("$.data[0].participantCount").value(4))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-07-23T14:10:00"))
                .andExpect(jsonPath("$.data[0].isHost").value(true));

        verify(meetingService).findMyMeetings(LOCAL_STORAGE_KEY);
    }

    @Test
    @DisplayName("내 모임 목록 조회 API는 localStorageKey가 없으면 400을 반환한다")
    void getMyMeetings_returnsBadRequestWhenLocalStorageKeyMissing() throws Exception {
        mockMvc.perform(get("/api/meetings/me"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
