// 알림 배치 트리거 + 알림 탭 흐름 동시 트래픽 (#52, S13~S15)
//
// setup()에서 배치를 트리거하고 시각을 남긴다. 이후 "받은 유저 중 OPEN_RATE가 지수분포 지연(중앙값 MEDIAN_S) 뒤
// 앱을 연다"는 도착 곡선으로 탭 흐름을 흘린다. 전원이 트리거 시점에 받았다고 근사한다(발송이 길면 비관적).
//
// 환경변수
//   BASE_URL    앱 주소 (compose 안: http://app:8080)
//   USERS_FILE  열람 후보 유저 목록 JSON [{id, email}] (run.sh가 정답지에서 뽑는다)
//   OPEN_RATE   후보 대비 앱을 여는 비율. 1을 넘으면 같은 유저가 여러 번 연다(ST4)
//   MEDIAN_S    열기까지 지연의 중앙값(초)
//   CAP_S       트래픽을 흘리는 최대 시간(초). 기본 10800 = 판정 창 3시간(S5)
//   JWT_SECRET  앱과 같은 perf 전용 키
//   SUMMARY     요약 JSON 경로
import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
const USERS = JSON.parse(open(__ENV.USERS_FILE));
const OPEN_RATE = Number(__ENV.OPEN_RATE || 0.144);
const MEDIAN_S = Number(__ENV.MEDIAN_S || 420);
const CAP_S = Number(__ENV.CAP_S || 10800);
const SECRET = __ENV.JWT_SECRET;

// 도착 곡선: r(t) = N·λ·e^(−λt), λ = ln2 / 중앙값. 99%가 열리는 시점(중앙값 × log2(100))이나 CAP_S에서 끊는다
const N_OPEN = Math.round(USERS.length * OPEN_RATE);
const LAMBDA = Math.LN2 / MEDIAN_S;
const END_S = Math.min(CAP_S, Math.ceil(MEDIAN_S * Math.log2(100)));
const STEPS = 60;
const STEP_S = Math.max(1, Math.ceil(END_S / STEPS));
const PEAK_PER_S = N_OPEN * LAMBDA;
// k6 도착률 목표는 정수라서, 최고점이 100 이상이 되도록 시간 단위를 늘린다(반올림 오차 ≤ 1%)
const UNIT_S = Math.max(1, Math.ceil(100 / Math.max(PEAK_PER_S, 1e-9)));
const rateAt = (t) => Math.round(N_OPEN * LAMBDA * Math.exp(-LAMBDA * t) * UNIT_S);

const stages = [];
for (let t = STEP_S; t <= END_S; t += STEP_S) {
    stages.push({ duration: `${STEP_S}s`, target: rateAt(t) });
}

export const options = {
    scenarios: N_OPEN === 0 ? {
        // 열람 트래픽 없이 배치만 트리거한다
        idle: { executor: 'shared-iterations', vus: 1, iterations: 1, exec: 'noop' },
    } : {
        tap: {
            executor: 'ramping-arrival-rate',
            exec: 'tapFlow',
            startRate: rateAt(0),
            timeUnit: `${UNIT_S}s`,
            stages,
            preAllocatedVUs: 50,
            maxVUs: 2000,
        },
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function b64url(data) {
    return encoding.b64encode(data, 'rawurl');
}

// 앱의 JwtProvider.createAccessToken과 같은 클레임. 세션(perf-s-{id})은 시드가 만든다
function accessToken(user) {
    const now = Math.floor(Date.now() / 1000);
    const header = b64url(JSON.stringify({ alg: 'HS256' }));
    const payload = b64url(JSON.stringify({
        sub: user.email, id: user.id, role: 'ROLE_USER', tokenType: 'access',
        sid: `perf-s-${user.id}`, iat: now, exp: now + 6 * 3600,
    }));
    const sig = b64url(crypto.hmac('sha256', SECRET, `${header}.${payload}`, 'binary'));
    return `${header}.${payload}.${sig}`;
}

export function setup() {
    const res = http.post(`${BASE_URL}/api/internal/perf/expiry-batch`);
    if (res.status !== 202) {
        exec.test.abort(`trigger failed: ${res.status}`);
    }
    const startMs = Date.now();
    console.log(`trigger start_ms=${startMs} n_open=${N_OPEN} unit=${UNIT_S}s end=${END_S}s`);
    return { start_ms: startMs, n_open: N_OPEN, candidates: USERS.length };
}

export function noop() {}

// 프론트 handleNotificationPress 기준: 읽음 처리 → 홈(냉장고 목록) → 알림 목록·안 읽은 수 갱신.
// 읽음 처리에 알림 id가 필요해 목록 조회를 먼저 한다(요청 4건은 같다)
export function tapFlow() {
    const user = USERS[exec.scenario.iterationInTest % USERS.length];
    const params = (name) => ({ headers: { Authorization: `Bearer ${accessToken(user)}` }, tags: { name } });

    const list = http.get(`${BASE_URL}/api/notifications?size=20`, params('notifications'));
    check(list, { 'notifications 200': (r) => r.status === 200 });
    const items = list.status === 200 ? (list.json('notifications') || []) : [];
    if (items.length > 0) {
        const read = http.patch(`${BASE_URL}/api/notifications/${items[0].id}/read`, null, params('read'));
        check(read, { 'read 2xx': (r) => r.status >= 200 && r.status < 300 });
    }
    const fridge = http.get(`${BASE_URL}/api/v1/user-ingredients`, params('user-ingredients'));
    check(fridge, { 'user-ingredients 200': (r) => r.status === 200 });
    const unread = http.get(`${BASE_URL}/api/notifications/unread-count`, params('unread-count'));
    check(unread, { 'unread-count 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    return { [__ENV.SUMMARY || '/runs/k6-summary.json']: JSON.stringify(data, null, 2) };
}
