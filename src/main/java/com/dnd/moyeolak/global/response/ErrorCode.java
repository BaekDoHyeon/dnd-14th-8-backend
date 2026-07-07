package com.dnd.moyeolak.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 요청 파라미터 / 검증 오류
    ERROR("E100", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD("E101", "필수 입력 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("E102", "입력 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("E103", "유효성 검증 실패", HttpStatus.BAD_REQUEST),

    // 클라이언트 오류
    UNAUTHORIZED("E401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("E403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    NOT_FOUND("E404", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE("E409", "이미 존재하는 리소스입니다.", HttpStatus.CONFLICT),
    MEETING_NOT_FOUND("E410", "모임이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    SCHEDULE_POLL_NOT_FOUND("E411", "일정 투표판이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    LOCATION_POLL_NOT_FOUND("E412", "위치 투표판이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_LOCAL_STORAGE_KEY("E413", "이미 참여한 사용자입니다.", HttpStatus.CONFLICT),
    PARTICIPANT_NOT_FOUND("E414", "참여자가 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    LOCATION_VOTE_NOT_FOUND("E415", "위치 응답이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    NO_LOCATION_VOTES("E416", "출발지가 등록되지 않았습니다.", HttpStatus.BAD_REQUEST),
    NO_NEARBY_STATIONS("E417", "반경 내 지하철역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GOOGLE_API_ERROR("E418", "외부 API 호출에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULE_VOTE_NOT_FOUND("E419", "시간 응답이 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    MEETING_EDIT_FORBIDDEN("E420", "모임 수정 권한이 없습니다.", HttpStatus.FORBIDDEN),
    PARTICIPANT_COUNT_BELOW_CURRENT("E421", "현재 참여 인원보다 적은 수로 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
    STATION_NOT_FOUND("E422", "지정한 역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ODSAY_API_ERROR("E423", "대중교통 경로 조회에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    KAKAO_API_ERROR("E424", "자동차 경로 조회에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INSUFFICIENT_LOCATION_VOTES("E425", "출발지 2개 이상 등록 시 중간지점을 확인할 수 있습니다.", HttpStatus.BAD_REQUEST),
    INVALID_TIME_RANGE("E426", "시작 시간은 종료 시간보다 빨라야 합니다.", HttpStatus.BAD_REQUEST),

    // 서버 오류
    SERVER_ERROR("E500", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
