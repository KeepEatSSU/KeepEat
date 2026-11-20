#!/usr/bin/env bash
# 측정 1회 실행 (#52): 초기화 → 시드 → mock 설정 → k6(트리거 + 탭 흐름) [+ 재시작] → receipt 대기 → 집계
#
# 환경변수
#   RUN_ID       실행 이름 (기본: run-날짜시각). 결과는 runs/<RUN_ID>/
#   시드         USERS, P_REGISTERED, P_EXPIRING, P_ALLOW, P_DDAY, P_EXPIRED_TOKEN, ITEMS, SEED (seed.sh 참고)
#   MOCK_CONFIG  mock 장애 설정 쿼리 문자열. 예: "rate5xx=1"
#   MOCK_RECOVER, MOCK_RECOVER_AFTER_S   트리거 N초 뒤 mock에 보낼 해제 설정. 예: "rate5xx=0", 60
#   OPEN_RATE, MEDIAN_S, CAP_S   탭 흐름 트래픽 (k6/tap-flow.js 참고). OPEN_RATE=0이면 트리거만 한다
#   RESTART      "<stop|kill> <after-trigger|after-last-send> <초>". 예: "kill after-trigger 2"
#   SETTLE_S     마지막 발송 뒤 receipt를 기다리는 최대 시간. 기본 720초(앱은 약 10분 뒤 확인을 포기한다)
set -euo pipefail
cd "$(dirname "$0")"

RUN_ID=${RUN_ID:-run-$(date +%Y%m%d-%H%M%S)}
DIR=runs/$RUN_ID
APP=localhost:${PERF_APP_PORT:-18080}
MOCK=localhost:${PERF_MOCK_PORT:-18089}
# 중간에 끝나도 백그라운드 작업(재시작·장애 해제)이 다음 실행의 컨테이너를 건드리지 않게 정리한다
# restart.sh는 종료 신호를 받아도 실행 중인 docker compose 명령이 끝난 뒤 나가므로, 끝날 때까지 기다린다
trap 'kill $(jobs -p) 2>/dev/null || true; wait' EXIT
psql() { docker compose exec -T db psql -U perf -d keepeat -v ON_ERROR_STOP=1 -qtA "$@"; }
now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

docker compose down -v >/dev/null 2>&1
docker compose up -d --wait >/dev/null 2>&1
./seed/seed.sh
curl -fsS -X POST "$MOCK/__perf/reset?runId=$RUN_ID" >/dev/null
if [ -n "${MOCK_CONFIG:-}" ]; then
    curl -fsS -X POST "$MOCK/__perf/config?$MOCK_CONFIG" >/dev/null
fi

# 탭 흐름 후보 = 알림을 받을 유저(도달률 분모와 같다)
psql -c "SELECT coalesce(json_agg(json_build_object('id', user_id, 'email', 'perf-u' || user_id || '@loadtest.local') ORDER BY user_id), '[]')
         FROM loadtest.expected_user WHERE expiring AND enabled AND has_token AND NOT expired_token" > "$DIR/users.json"

if [ -n "${MOCK_RECOVER:-}" ]; then
    (
        until grep -q 'start_ms=' "$DIR/k6.log" 2>/dev/null; do sleep 0.1; done
        sleep "${MOCK_RECOVER_AFTER_S:-60}"
        curl -fsS -X POST "$MOCK/__perf/config?$MOCK_RECOVER" >/dev/null
    ) &
    RECOVER_PID=$!
fi

if [ -n "${RESTART:-}" ]; then
    # shellcheck disable=SC2086
    ./restart.sh "$RUN_ID" $RESTART &
    RESTART_PID=$!
fi

docker compose --profile tools run --rm \
    -e USERS_FILE=/runs/$RUN_ID/users.json -e SUMMARY=/runs/$RUN_ID/k6-summary.json \
    -e OPEN_RATE="${OPEN_RATE:-0}" -e MEDIAN_S="${MEDIAN_S:-420}" -e CAP_S="${CAP_S:-10800}" \
    k6 run --quiet /scripts/tap-flow.js 2>&1 | tee "$DIR/k6.log" | grep -E "start_ms=|ERRO|WARN" || true
START_MS=$(grep -o 'start_ms=[0-9]*' "$DIR/k6.log" | head -1 | cut -d= -f2 || true)
if [ -z "$START_MS" ]; then
    echo "k6가 배치를 트리거하지 못했다 ($DIR/k6.log 확인)" >&2
    exit 1
fi
if [ -n "${RESTART_PID:-}" ] && ! wait "$RESTART_PID"; then
    echo "재시작 작업이 실패했다. 이 실행 결과는 신뢰할 수 없다 ($DIR/restart.log 확인)" >&2
    exit 1
fi
# 복구가 적용된 뒤에 집계하도록 기다린다
if [ -n "${RECOVER_PID:-}" ] && ! wait "$RECOVER_PID"; then
    echo "MOCK_RECOVER 요청이 실패했다. 이 실행의 장애 해제 결과는 신뢰할 수 없다" >&2
    exit 1
fi

# 발송이 멈추고 ticket ok 수와 receipt ok 수가 같아지거나, 마지막 발송 뒤 SETTLE_S가 지날 때까지 기다린다
LOG=$DIR/mock.jsonl
last_change=$(now_ms) last=-1
while :; do
    sends=$(grep -c '"ev":"\(send\|ticket\)"' "$LOG" || true)
    tickets=$(grep -c '"ev":"ticket".*"outcome":"ok' "$LOG" || true)
    receipts=$(grep '"ev":"receipts"' "$LOG" | grep -o '[0-9a-f]\{8\}-[0-9a-f-]\{27\}' | sort -u | wc -l | tr -d ' ' || true)
    [ "$sends" != "$last" ] && { last=$sends; last_change=$(now_ms); }
    # 재시도 중인 발송이 남아 있을 수 있으므로 발송이 30초(재시도 3회 × 타임아웃 6초보다 길게) 멈춘 뒤에만 끝낸다.
    # ponytail: mock에 기록되지 않는 앱 내부 대기(큐)는 못 본다. 근사 조건이다
    quiet=$(( $(now_ms) - last_change ))
    [ "$tickets" -gt 0 ] && [ "$tickets" = "$receipts" ] && [ "$quiet" -gt 30000 ] && break
    [ $(( $(now_ms) - last_change )) -gt $(( ${SETTLE_S:-720} * 1000 )) ] && break
    sleep 5
done
echo "run=$RUN_ID waited $(( ($(now_ms) - START_MS) / 1000 ))s after trigger (ok tickets=$tickets, ok receipts=$receipts)"

./aggregate/aggregate.sh "$RUN_ID" "$START_MS"

# k6 요약: 탭 흐름 요청의 실패율과 지연
python3 - "$DIR/k6-summary.json" <<'EOF'
import json, sys
m = json.load(open(sys.argv[1]))["metrics"]
reqs = m.get("http_reqs", {}).get("values", {}).get("count", 0)
print("\n== 탭 흐름 트래픽 (k6) ==")
print(f"requests={reqs} failed_rate={m.get('http_req_failed', {}).get('values', {}).get('rate', 0):.4f}")
d = m.get("http_req_duration", {}).get("values")
if d:
    print("duration ms: " + " ".join(f"{k}={v:.1f}" for k, v in d.items()))
EOF
