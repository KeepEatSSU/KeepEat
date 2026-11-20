#!/usr/bin/env bash
# Smoke S1 (#52): 측정 도구 검증. 대상 10명에서 집계가 정답지와 정확히 일치하는지 본다.
# 구성(seed 0.083): 임박 10명 = 정상 7(D-day 3) + 앱 끔 1 + 토큰 없음 1 + 만료 토큰 1, 대상 아님 10명
set -euo pipefail
cd "$(dirname "$0")"

RUN_ID=${RUN_ID:-smoke-$(date +%Y%m%d-%H%M%S)}
now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

docker compose down -v >/dev/null 2>&1
docker compose up -d --wait >/dev/null 2>&1
USERS=20 P_REGISTERED=0.75 P_EXPIRING=0.67 P_ALLOW=0.8 P_DDAY=0.3 P_EXPIRED_TOKEN=0.12 SEED=0.083 ./seed/seed.sh
curl -fsS -X POST "localhost:${PERF_MOCK_PORT:-18089}/__perf/reset?runId=$RUN_ID" >/dev/null

START_MS=$(now_ms)
curl -fsS -X POST "localhost:${PERF_APP_PORT:-18080}/api/internal/perf/expiry-batch" >/dev/null
echo "run=$RUN_ID start_ms=$START_MS"

# ticket ok 수와 receipt ok 수가 같아질 때까지 기다린다. 앱은 약 10분 뒤 확인을 포기하므로 12분에서 끊는다
LOG=runs/$RUN_ID/mock.jsonl
for _ in $(seq 144); do
    tickets=$(grep -c '"ev":"ticket".*"outcome":"ok' "$LOG" || true)
    receipts=$(grep '"ev":"receipts"' "$LOG" | grep -o '[0-9a-f]\{8\}-[0-9a-f-]\{27\}' | sort -u | wc -l | tr -d ' ' || true)
    [ "$tickets" -gt 0 ] && [ "$tickets" = "$receipts" ] && break
    sleep 5
done
echo "waited $(( ($(now_ms) - START_MS) / 1000 ))s (ok tickets=$tickets, ok receipts=$receipts)"

./aggregate/aggregate.sh "$RUN_ID" "$START_MS"

# 판정: 모든 단계에서 기대 = 실제이고, 예상 밖 동작이 없어야 한다
MISMATCH=$(docker compose exec -T db psql -U perf -d keepeat -tAc "
SELECT count(*) FROM perf.user_result WHERE
       saved <> expiring
    OR (requests > 0) <> expect_request
    OR (ok_tickets > 0) <> in_denominator
    OR reached <> in_denominator
    OR (dnr > 0) <> (expect_request AND expired_token)
    OR ok_tickets <> ok_notifications
    OR failed_requests > 0
    OR (expect_request AND expired_token AND token_left)")
if [ "$MISMATCH" = "0" ]; then
    echo "SMOKE PASS: 집계가 정답지와 정확히 일치한다"
else
    echo "SMOKE FAIL: 불일치 유저 ${MISMATCH}명 (perf.user_result 확인)"
    exit 1
fi
