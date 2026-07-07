/**
 * GET /api/locations/midpoint-routes 성능 테스트
 *
 * mode 파라미터별로 분리 측정 → Google Routes vs Kakao 병목 파악 가능
 *
 * 실행 예시:
 *   k6 run k6/midpoint-routes.js                             # both + load (기본)
 *   k6 run -e MODE=transit  k6/midpoint-routes.js            # Google Routes만
 *   k6 run -e MODE=driving  k6/midpoint-routes.js            # Kakao만
 *   k6 run -e SCENARIO=smoke k6/midpoint-routes.js           # smoke
 *   k6 run -e PARTICIPANT_ID=2 k6/midpoint-routes.js         # 다른 참여자
 *
 * Grafana 연동:
 *   k6 run --out influxdb=http://localhost:8086/k6 k6/midpoint-routes.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import {
    BASE_URL,
    MEETING_ID,
    STATION_ID,
    PARTICIPANT_ID,
    DEPARTURE_TIME,
    SCENARIO,
    STAGES,
} from './common/config.js';

// MODE: transit | driving | both (기본: both)
const MODE = __ENV.MODE || 'both';

// ── 커스텀 메트릭 (mode별 응답시간 분리 측정) ──────────────────────────────────
const transitDuration = new Trend('transit_duration_ms', true);
const drivingDuration = new Trend('driving_duration_ms', true);
const bothDuration    = new Trend('both_duration_ms',    true);
const errorRate       = new Rate('error_rate');

// ── 옵션 ─────────────────────────────────────────────────────────────────────
export const options = {
    stages: STAGES[SCENARIO],
    thresholds: {
        // transit: Google Routes API (매트릭스 1회 + Top 3 Kakao 보강)
        transit_duration_ms: ['p(95)<8000'],
        // driving: Kakao Directions API (응답 빠름)
        driving_duration_ms: ['p(95)<5000'],
        // both: 두 API 순차 호출 (가장 느림)
        both_duration_ms:    ['p(95)<10000'],
        // 전체 실패율
        http_req_failed:     ['rate<0.01'],
        error_rate:          ['rate<0.01'],
    },
};

// ── URL 빌드 ─────────────────────────────────────────────────────────────────
function buildUrl(mode) {
    let url = `${BASE_URL}/api/locations/midpoint-routes`
        + `?meetingId=${MEETING_ID}`
        + `&stationId=${STATION_ID}`
        + `&participantId=${PARTICIPANT_ID}`
        + `&mode=${mode}`;
    if (DEPARTURE_TIME) {
        url += `&departureTime=${encodeURIComponent(DEPARTURE_TIME)}`;
    }
    return url;
}

// ── mode별 검증 함수 ──────────────────────────────────────────────────────────
// body.data가 null/undefined 둘 다 커버하기 위해 옵셔널 체이닝 사용
// (API 에러 시 @JsonInclude(NON_NULL)로 data 필드 자체가 사라져 undefined가 됨)
function checkTransitRoute(body) {
    return typeof body.data?.transit?.durationMinutes === 'number';
}

function checkDrivingRoute(body) {
    return typeof body.data?.driving?.durationMinutes === 'number';
}

// ── 메인 테스트 함수 ──────────────────────────────────────────────────────────
export default function () {
    const url = buildUrl(MODE);

    group(`GET /midpoint-routes [mode=${MODE}]`, () => {
        const res = http.get(url, {
            tags: { scenario: SCENARIO, mode: MODE, endpoint: 'midpoint-routes' },
        });

        let body = null;
        try {
            body = JSON.parse(res.body);
        } catch {
            console.error(`[FAIL] JSON 파싱 실패: ${res.body.substring(0, 200)}`);
            errorRate.add(1);
            return;
        }

        // ── 공통 검증 ────────────────────────────────────────────────────────
        const baseChecks = check(res, {
            'status is 200':          (r) => r.status === 200,
            'has data field':         () => body.data != null,
            'participant info exists': () => body.data?.participant?.participantId != null,
            'station info exists':     () => body.data?.station?.stationId != null,
        });

        // ── mode별 추가 검증 ─────────────────────────────────────────────────
        if (MODE === 'transit' || MODE === 'both') {
            check(res, {
                'transit route exists': () => checkTransitRoute(body),
            });
        }
        if (MODE === 'driving' || MODE === 'both') {
            check(res, {
                'driving route exists': () => checkDrivingRoute(body),
            });
        }

        // ── mode별 응답시간 메트릭 기록 ───────────────────────────────────────
        const duration = res.timings.duration;
        if (MODE === 'transit') {
            transitDuration.add(duration);
        } else if (MODE === 'driving') {
            drivingDuration.add(duration);
        } else {
            bothDuration.add(duration);
        }

        // ── 실패 시 에러율 기록 ───────────────────────────────────────────────
        const failed = !baseChecks || res.status !== 200;
        errorRate.add(failed ? 1 : 0);

        if (failed) {
            console.error(
                `[FAIL] mode=${MODE} status=${res.status} duration=${duration}ms body=${res.body.substring(0, 300)}`
            );
        }
    });

    // transit은 Google Routes 대기가 있을 수 있으므로 sleep을 줄여 경쟁 상황 유도
    sleep(MODE === 'transit' ? 0.5 : 1);
}

// ── 테스트 종료 후 요약 출력 ───────────────────────────────────────────────────
export function handleSummary(data) {
    const metrics = data.metrics;
    const totalReqs   = metrics.http_reqs?.values?.count ?? 0;
    const p95Total    = metrics.http_req_duration?.values?.['p(95)'] ?? 0;
    const errRate     = metrics.error_rate?.values?.rate ?? 0;

    const transitP95  = metrics.transit_duration_ms?.values?.['p(95)'] ?? null;
    const drivingP95  = metrics.driving_duration_ms?.values?.['p(95)'] ?? null;
    const bothP95     = metrics.both_duration_ms?.values?.['p(95)'] ?? null;

    const modeMetric = transitP95 !== null ? `transit p95 : ${transitP95.toFixed(0)}ms`
        : drivingP95 !== null              ? `driving p95 : ${drivingP95.toFixed(0)}ms`
        : bothP95 !== null                 ? `both    p95 : ${bothP95.toFixed(0)}ms`
        : '(측정값 없음)';

    const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  midpoint-routes 성능 테스트 결과
  시나리오: ${SCENARIO} | mode: ${MODE}
  meetingId: ${MEETING_ID} | stationId: ${STATION_ID} | participantId: ${PARTICIPANT_ID}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  전체 요청 수       : ${totalReqs}
  에러율             : ${(errRate * 100).toFixed(2)}%
  전체 p95 응답시간  : ${p95Total.toFixed(0)}ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  mode별 p95
  ${modeMetric}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  [참고] transit p95 > driving p95 이면 Google Routes API 대기가 병목
  [참고] both p95 ≈ transit p95 + driving p95 이면 순차 호출 정상 동작
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;

    return {
        stdout: summary,
    };
}