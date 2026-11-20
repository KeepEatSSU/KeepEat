#!/usr/bin/env bash
# Smoke S1 (#52): 측정 도구 검증. 대상 10명에서 집계가 정답지와 정확히 일치하는지 본다.
# 구성(seed 0.083): 임박 10명 = 정상 7(D-day 3) + 앱 끔 1 + 토큰 없음 1 + 만료 토큰 1, 대상 아님 10명
# 탭 흐름도 함께 흘려(OPEN_RATE=1, 중앙값 5초) 인증·API 경로까지 확인한다.
set -euo pipefail
cd "$(dirname "$0")"

export RUN_ID=${RUN_ID:-smoke-$(date +%Y%m%d-%H%M%S)}
USERS=20 P_REGISTERED=0.75 P_EXPIRING=0.67 P_ALLOW=0.8 P_DDAY=0.3 P_EXPIRED_TOKEN=0.12 SEED=0.083 \
    OPEN_RATE=1 MEDIAN_S=5 ./run.sh

# 판정: 모든 단계에서 기대 = 실제이고, 예상 밖 동작이 없어야 한다. 탭 흐름 요청도 전부 성공해야 한다
MISMATCH=$(docker compose exec -T db psql -U perf -d keepeat -tAc "
SELECT count(*) FROM loadtest.user_result WHERE
       saved <> expiring
    OR (requests > 0) <> expect_request
    OR (ok_tickets > 0) <> in_denominator
    OR reached <> in_denominator
    OR (dnr > 0) <> (expect_request AND expired_token)
    OR ok_tickets <> ok_notifications
    OR failed_requests > 0
    OR (expect_request AND expired_token AND token_left)")
FAILED=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['metrics']['http_req_failed']['values']['passes'])" "runs/$RUN_ID/k6-summary.json")
if [ "$MISMATCH" = "0" ] && [ "$FAILED" = "0" ]; then
    echo "SMOKE PASS: 집계가 정답지와 정확히 일치하고 탭 흐름 요청이 모두 성공했다"
else
    echo "SMOKE FAIL: 불일치 유저 ${MISMATCH}명, 실패한 탭 흐름 요청 ${FAILED}건"
    exit 1
fi
