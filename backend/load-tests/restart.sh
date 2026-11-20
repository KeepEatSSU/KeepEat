#!/usr/bin/env bash
# 재시작 제어 (#52, F1). run.sh가 k6와 나란히 백그라운드로 띄운다.
# 사용법: ./restart.sh <runId> <stop|kill> <after-trigger|after-last-send> <초>
#   after-trigger N     트리거 N초 뒤 (F1-a: 2, F1-b: 10 — 큐가 가장 길 때를 대략 시작 10초 뒤로 근사)
#   after-last-send N   mock에 마지막 발송이 들어오고 N초 뒤 (F1-c: 60, 300). 5초 동안 새 발송이 없으면 끝난 것으로 본다
#   stop = docker stop(SIGTERM, 정상 종료) / kill = docker kill(SIGKILL, 강제 종료)
# 환경변수: RESTART_SERVICE 내릴 서비스(기본 app, F6은 db) / DOWN_S 내린 뒤 다시 띄우기까지 기다릴 초(기본 0)
set -euo pipefail
cd "$(dirname "$0")"
# 종료 신호를 받으면 실행 중인 docker compose 명령이 끝난 뒤 나간다(bash는 포그라운드 명령이 끝난 다음 trap을 실행한다)
trap 'exit 143' TERM
RUN_ID=$1 HOW=$2 WHEN=$3 SECS=$4
SVC=${RESTART_SERVICE:-app}
DIR=runs/$RUN_ID
now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }
log() { echo "$(now_ms) $*" | tee -a "$DIR/restart.log"; }

# k6 setup()이 트리거하면서 남기는 시각을 기다린다
until START_MS=$(grep -o 'start_ms=[0-9]*' "$DIR/k6.log" 2>/dev/null | head -1 | cut -d= -f2) && [ -n "$START_MS" ]; do
    sleep 0.1
done

case $WHEN in
    after-trigger)
        TARGET=$((START_MS + SECS * 1000))
        ;;
    after-last-send)
        last=0 same=0
        DEADLINE=$(( START_MS + 600000 ))  # 10분 안에 발송이 하나도 없으면 재시작을 건너뛴다
        while [ $same -lt 50 ]; do  # 0.1초 × 50 = 5초
            n=$(grep -c '"ev":"\(send\|ticket\)"' "$DIR/mock.jsonl" || true)
            if [ "$n" -gt 0 ] && [ "$n" = "$last" ]; then same=$((same + 1)); else same=0; last=$n; fi
            if [ "$n" = "0" ] && [ "$(now_ms)" -gt "$DEADLINE" ]; then
                log "no send within 600s, restart skipped"
                exit 0
            fi
            sleep 0.1
        done
        log "last send detected (sends=$last)"
        TARGET=$(( $(now_ms) - 5000 + SECS * 1000 ))
        ;;
    *) echo "unknown timing: $WHEN" >&2; exit 2 ;;
esac

while [ "$(now_ms)" -lt "$TARGET" ]; do sleep 0.05; done
log "$HOW $SVC (planned=+$(( (TARGET - START_MS) / 1000 ))s from trigger)"
case $HOW in
    stop) docker compose stop "$SVC" >/dev/null 2>&1 ;;
    kill) docker compose kill "$SVC" >/dev/null 2>&1 ;;
    *) echo "unknown method: $HOW" >&2; exit 2 ;;
esac
log "$SVC down"
sleep "${DOWN_S:-0}"
docker compose start "$SVC" >/dev/null 2>&1
docker compose up -d --wait "$SVC" >/dev/null 2>&1
log "$SVC up"
