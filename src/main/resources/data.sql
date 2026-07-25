-- 로컬 재기동 멱등성: 시드 대상 도메인 테이블 초기화 (stations는 Flyway 시드)
TRUNCATE TABLE meeting CASCADE;

-- =====================================================
-- 초기 테스트 데이터: 모임 10개 (각 모임당 방장 1명)
-- =====================================================

-- Meeting 10개
INSERT INTO meeting (meeting_id, participant_count, initial_flow, created_at, updated_at) VALUES
('test-meeting-001', 10, 'SCHEDULE', NOW(), NOW()),
('test-meeting-002', 4, 'SCHEDULE', NOW(), NOW()),
('test-meeting-003', 6, 'SCHEDULE', NOW(), NOW()),
('test-meeting-004', 3, 'SCHEDULE', NOW(), NOW()),
('test-meeting-005', 8, 'SCHEDULE', NOW(), NOW()),
('test-meeting-006', 2, 'SCHEDULE', NOW(), NOW()),
('test-meeting-007', 7, 'SCHEDULE', NOW(), NOW()),
('test-meeting-008', 4, 'SCHEDULE', NOW(), NOW()),
('test-meeting-009', 5, 'SCHEDULE', NOW(), NOW()),
('test-meeting-010', 6, 'SCHEDULE', NOW(), NOW());

-- SchedulePoll 10개 (각 모임당 1개, 14일간 날짜 옵션)
INSERT INTO schedule_poll (meeting_id, date_options, start_time, end_time, confirmed_start_time, confirmed_end_time, poll_status, created_at, updated_at) VALUES
('test-meeting-001', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-002', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-003', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-004', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-005', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-006', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-007', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-008', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-009', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW()),
('test-meeting-010', '["2025-02-05","2025-02-06","2025-02-07","2025-02-08","2025-02-09","2025-02-10","2025-02-11","2025-02-12","2025-02-13","2025-02-14","2025-02-15","2025-02-16","2025-02-17","2025-02-18"]', 420, 1440, NULL, NULL, 'INACTIVE', NOW(), NOW());

-- LocationPoll 10개 (각 모임당 1개)
INSERT INTO location_poll (meeting_id, poll_status, created_at, updated_at) VALUES
('test-meeting-001', 'INACTIVE', NOW(), NOW()),
('test-meeting-002', 'INACTIVE', NOW(), NOW()),
('test-meeting-003', 'INACTIVE', NOW(), NOW()),
('test-meeting-004', 'INACTIVE', NOW(), NOW()),
('test-meeting-005', 'INACTIVE', NOW(), NOW()),
('test-meeting-006', 'INACTIVE', NOW(), NOW()),
('test-meeting-007', 'INACTIVE', NOW(), NOW()),
('test-meeting-008', 'INACTIVE', NOW(), NOW()),
('test-meeting-009', 'INACTIVE', NOW(), NOW()),
('test-meeting-010', 'INACTIVE', NOW(), NOW());

INSERT INTO participant (meeting_id, local_storage_key, name, is_host, created_at, updated_at) VALUES
('test-meeting-001', 'host-key-001', '김민준', TRUE, NOW(), NOW()),
('test-meeting-001', 'key-001-02', '이서연', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-03', '박도윤', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-04', '최하은', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-05', '백도현', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-06', '홍길동', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-07', '백무식', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-08', '차은지', FALSE, NOW(), NOW()),
('test-meeting-001', 'key-001-09', '김하온', FALSE, NOW(), NOW()),
('test-meeting-002', 'host-key-002', '이서연', TRUE, NOW(), NOW()),
('test-meeting-002', 'key-002-02', '정시우', FALSE, NOW(), NOW()),
('test-meeting-002', 'key-002-03', '강지아', FALSE, NOW(), NOW()),
('test-meeting-002', 'key-002-04', '조예준', FALSE, NOW(), NOW()),
('test-meeting-003', 'host-key-003', '박도윤', TRUE, NOW(), NOW()),
('test-meeting-003', 'key-003-02', '윤수아', FALSE, NOW(), NOW()),
('test-meeting-003', 'key-003-03', '임건우', FALSE, NOW(), NOW()),
('test-meeting-003', 'key-003-04', '한지유', FALSE, NOW(), NOW()),
('test-meeting-004', 'host-key-004', '최하은', TRUE, NOW(), NOW()),
('test-meeting-005', 'host-key-005', '정시우', TRUE, NOW(), NOW()),
('test-meeting-006', 'host-key-006', '강지아', TRUE, NOW(), NOW()),
('test-meeting-007', 'host-key-007', '조예준', TRUE, NOW(), NOW()),
('test-meeting-008', 'host-key-008', '윤수아', TRUE, NOW(), NOW()),
('test-meeting-009', 'host-key-009', '임건우', TRUE, NOW(), NOW()),
('test-meeting-010', 'host-key-010', '한지유', TRUE, NOW(), NOW());

-- =====================================================
-- 일정 투표 테스트용 더미 데이터 (test-meeting-001)
-- 시나리오: 참가자 1~8번이 투표, 9번(김하온)·10번(양우렉)은 미투표
-- 인기 시간대: 2/8(토) 18:00~20:00, 2/9(일) 14:00~16:00
-- =====================================================

-- 1번 김민준 (방장) - 수요일·목요일 저녁 + 주말 오후~저녁
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-05T19:00:00","2025-02-05T19:30:00","2025-02-05T20:00:00","2025-02-06T19:00:00","2025-02-06T19:30:00","2025-02-06T20:00:00","2025-02-08T14:00:00","2025-02-08T14:30:00","2025-02-08T15:00:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-09T14:00:00","2025-02-09T14:30:00","2025-02-09T15:00:00","2025-02-09T15:30:00","2025-02-12T19:00:00","2025-02-12T19:30:00","2025-02-12T20:00:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '김민준'
WHERE sp.meeting_id = 'test-meeting-001';

-- 2번 이서연 - 화요일·수요일 저녁 + 토요일 저녁, 일요일 오후
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-05T19:00:00","2025-02-05T19:30:00","2025-02-05T20:00:00","2025-02-05T20:30:00","2025-02-11T19:30:00","2025-02-11T20:00:00","2025-02-11T20:30:00","2025-02-08T17:00:00","2025-02-08T17:30:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-08T20:00:00","2025-02-09T13:00:00","2025-02-09T13:30:00","2025-02-09T14:00:00","2025-02-09T14:30:00","2025-02-09T15:00:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '이서연'
WHERE sp.meeting_id = 'test-meeting-001';

-- 3번 박도윤 - 수요일·금요일 저녁 + 토요일 저녁 집중
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-05T18:30:00","2025-02-05T19:00:00","2025-02-05T19:30:00","2025-02-07T19:00:00","2025-02-07T19:30:00","2025-02-07T20:00:00","2025-02-12T19:00:00","2025-02-12T19:30:00","2025-02-12T20:00:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-08T20:00:00","2025-02-08T20:30:00","2025-02-09T14:00:00","2025-02-09T14:30:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '박도윤'
WHERE sp.meeting_id = 'test-meeting-001';

-- 4번 최하은 - 목요일 저녁 + 주말 넓게 투표
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-06T18:30:00","2025-02-06T19:00:00","2025-02-06T19:30:00","2025-02-06T20:00:00","2025-02-13T19:00:00","2025-02-13T19:30:00","2025-02-13T20:00:00","2025-02-08T12:00:00","2025-02-08T12:30:00","2025-02-08T13:00:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-09T14:00:00","2025-02-09T14:30:00","2025-02-09T15:00:00","2025-02-09T15:30:00","2025-02-09T16:00:00","2025-02-09T16:30:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '최하은'
WHERE sp.meeting_id = 'test-meeting-001';

-- 5번 백도현 - 월요일·수요일 저녁 + 토요일 저녁 강하게
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-10T19:00:00","2025-02-10T19:30:00","2025-02-10T20:00:00","2025-02-05T19:00:00","2025-02-05T19:30:00","2025-02-12T19:00:00","2025-02-12T19:30:00","2025-02-12T20:00:00","2025-02-08T17:30:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-08T20:00:00","2025-02-08T20:30:00","2025-02-08T21:00:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '백도현'
WHERE sp.meeting_id = 'test-meeting-001';

-- 6번 홍길동 - 화요일·목요일 저녁 + 일요일 오후 집중, 토요일 저녁 일부
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-11T19:00:00","2025-02-11T19:30:00","2025-02-11T20:00:00","2025-02-06T19:00:00","2025-02-06T19:30:00","2025-02-06T20:00:00","2025-02-13T19:00:00","2025-02-13T19:30:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-09T12:00:00","2025-02-09T12:30:00","2025-02-09T13:00:00","2025-02-09T13:30:00","2025-02-09T14:00:00","2025-02-09T14:30:00","2025-02-09T15:00:00","2025-02-09T15:30:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '홍길동'
WHERE sp.meeting_id = 'test-meeting-001';

-- 7번 백무식 - 수요일·금요일 저녁 + 금토 저녁
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-05T19:00:00","2025-02-05T19:30:00","2025-02-05T20:00:00","2025-02-05T20:30:00","2025-02-07T18:00:00","2025-02-07T18:30:00","2025-02-07T19:00:00","2025-02-12T18:30:00","2025-02-12T19:00:00","2025-02-12T19:30:00","2025-02-14T19:00:00","2025-02-14T19:30:00","2025-02-14T20:00:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-08T19:30:00","2025-02-08T20:00:00","2025-02-09T15:00:00","2025-02-09T15:30:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '백무식'
WHERE sp.meeting_id = 'test-meeting-001';

-- 8번 차은지 - 목요일·금요일 저녁 + 토요일 오후~저녁 넓게
INSERT INTO schedule_vote (schedule_poll_id, participant_id, voted_date, created_at, updated_at)
SELECT sp.schedule_poll_id, p.participant_id,
'["2025-02-06T19:00:00","2025-02-06T19:30:00","2025-02-06T20:00:00","2025-02-07T19:00:00","2025-02-07T19:30:00","2025-02-07T20:00:00","2025-02-13T19:00:00","2025-02-13T19:30:00","2025-02-13T20:00:00","2025-02-08T15:00:00","2025-02-08T15:30:00","2025-02-08T16:00:00","2025-02-08T16:30:00","2025-02-08T17:00:00","2025-02-08T17:30:00","2025-02-08T18:00:00","2025-02-08T18:30:00","2025-02-08T19:00:00","2025-02-09T14:00:00","2025-02-09T14:30:00","2025-02-09T15:00:00"]',
NOW(), NOW()
FROM schedule_poll sp JOIN participant p ON p.meeting_id = sp.meeting_id AND p.name = '차은지'
WHERE sp.meeting_id = 'test-meeting-001';

-- =====================================================
-- 중간지점 추천 테스트용 더미 데이터 (test-meeting-001)
-- 시나리오: 수원, 강남, 일산, 인천에서 출발하는 4명
-- 무게중심이 서울 중심부(홍대~신촌 부근)에 잡히도록 설정
-- 테스트: GET /api/locations/midpoint-recommendations?meetingId=test-meeting-001
-- =====================================================
INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '김민준', '수원시 영통구 영통동', 37.2553, 127.0726, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '김민준'
WHERE lp.meeting_id = 'test-meeting-001';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '이서연', '서울시 강남구 역삼동', 37.4979, 127.0276, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '이서연'
WHERE lp.meeting_id = 'test-meeting-001';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '박도윤', '고양시 일산동구 장항동', 37.6584, 126.7737, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '박도윤'
WHERE lp.meeting_id = 'test-meeting-001';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '최하은', '인천시 남동구 구월동', 37.4486, 126.7052, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '최하은'
WHERE lp.meeting_id = 'test-meeting-001';

-- =====================================================
-- 중간지점 추천 테스트용 더미 데이터 (test-meeting-002)
-- 시나리오: 서울 내 4개 지역 (강남, 홍대, 잠실, 노원)
-- 무게중심이 서울 중심부(종로~을지로 부근)에 잡히도록 설정
-- 테스트: GET /api/locations/midpoint-recommendations?meetingId=test-meeting-002
-- =====================================================
INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '이서연', '서울시 강남구 역삼동', 37.4979, 127.0276, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '이서연'
WHERE lp.meeting_id = 'test-meeting-002';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '정시우', '서울시 마포구 서교동', 37.5563, 126.9220, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '정시우'
WHERE lp.meeting_id = 'test-meeting-002';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '강지아', '서울시 송파구 잠실동', 37.5133, 127.1001, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '강지아'
WHERE lp.meeting_id = 'test-meeting-002';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '조예준', '서울시 노원구 상계동', 37.6542, 127.0568, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '조예준'
WHERE lp.meeting_id = 'test-meeting-002';

-- =====================================================
-- 중간지점 추천 테스트용 더미 데이터 (test-meeting-004)
-- 시나리오: 출발지 1개만 등록된 경우 → E425 에러 발생
-- 테스트: GET /api/locations/midpoint-recommendations?meetingId=test-meeting-004
-- =====================================================
INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '최하은', '서울시 강남구 삼성동', 37.5140, 127.0575, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '최하은'
WHERE lp.meeting_id = 'test-meeting-004';

-- =====================================================
-- 중간지점 추천 테스트용 더미 데이터 (test-meeting-003)
-- 시나리오: 경기도 내 4개 지역 (수원, 성남, 용인, 안양)
-- 무게중심이 경기 중부(과천~의왕 부근)에 잡히도록 설정
-- 테스트: GET /api/locations/midpoint-recommendations?meetingId=test-meeting-003
-- =====================================================
INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '박도윤', '수원시 영통구 영통동', 37.2553, 127.0726, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '박도윤'
WHERE lp.meeting_id = 'test-meeting-003';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '윤수아', '성남시 분당구 정자동', 37.3595, 127.1086, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '윤수아'
WHERE lp.meeting_id = 'test-meeting-003';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '임건우', '용인시 수지구 죽전동', 37.3245, 127.1070, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '임건우'
WHERE lp.meeting_id = 'test-meeting-003';

INSERT INTO location_vote (location_poll_id, participant_id, departure_name, departure_location, departure_lat, departure_lng, created_at, updated_at)
SELECT lp.location_poll_id, p.participant_id, '한지유', '안양시 동안구 평촌동', 37.3943, 126.9568, NOW(), NOW()
FROM location_poll lp JOIN participant p ON p.meeting_id = lp.meeting_id AND p.name = '한지유'
WHERE lp.meeting_id = 'test-meeting-003';
