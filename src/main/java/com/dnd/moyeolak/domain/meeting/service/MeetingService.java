package com.dnd.moyeolak.domain.meeting.service;

import com.dnd.moyeolak.domain.meeting.dto.CreateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleResponse;
import com.dnd.moyeolak.domain.meeting.dto.GetMeetingScheduleVoteResultResponse;
import com.dnd.moyeolak.domain.meeting.dto.LandingStatsResponse;
import com.dnd.moyeolak.domain.meeting.dto.UpdateMeetingRequest;
import com.dnd.moyeolak.domain.meeting.entity.Meeting;

import java.util.List;

public interface MeetingService {

    String createMeeting(CreateMeetingRequest request);

    void updateMeeting(UpdateMeetingRequest request);

    Meeting get(String meetingId);

    GetMeetingScheduleResponse getMeetingSchedules(String meetingId);

    GetMeetingScheduleVoteResultResponse getMeetingScheduleVoteResults(String meetingId);

    LandingStatsResponse getLandingStats();

    List<String> findAllMeetings();

    void deleteMeeting(String meetingId);
}
