#!/usr/bin/env bash
# 집계 (#52). 사용법: ./aggregate/aggregate.sh <runId> <배치 트리거 epoch ms>
set -euo pipefail
cd "$(dirname "$0")/.."
RUN_ID=$1
START_MS=$2

psql() { docker compose exec -T db psql -U perf -d keepeat -v ON_ERROR_STOP=1 -q "$@"; }

psql -c "DROP TABLE IF EXISTS loadtest.mock_event; CREATE TABLE loadtest.mock_event (e jsonb);"
# 한 줄을 그대로 한 값으로 읽기 위해, 나올 수 없는 문자를 quote·delimiter로 쓴다
psql -c "COPY loadtest.mock_event (e) FROM STDIN WITH (FORMAT csv, QUOTE e'\x01', DELIMITER e'\x02')" \
    < "runs/$RUN_ID/mock.jsonl"
psql -v start_ms="$START_MS" < aggregate/aggregate.sql
