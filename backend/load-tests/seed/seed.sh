#!/usr/bin/env bash
# 시드 투입 (#52). 앱이 기동해 Flyway 스키마가 만들어진 뒤 실행한다.
# 기본값은 Load L1 기준 조합이다. 환경변수로 바꾼다: USERS=500 P_ALLOW=0.9 ./seed/seed.sh
set -euo pipefail
cd "$(dirname "$0")/.."

psql() { docker compose exec -T db psql -U perf -d keepeat -v ON_ERROR_STOP=1 -q "$@"; }

# 마스터 데이터 (S9: perf 단독 프로필이라 data.sql이 자동으로 들어가지 않는다)
if [ "$(psql -tAc 'select count(*) from ingredient')" = "0" ]; then
    psql < ../src/main/resources/data.sql
fi

psql \
    -v users="${USERS:-10000}" \
    -v p_registered="${P_REGISTERED:-0.5}" \
    -v p_expiring="${P_EXPIRING:-0.3}" \
    -v p_allow="${P_ALLOW:-0.5}" \
    -v p_dday="${P_DDAY:-0.33}" \
    -v p_expired_token="${P_EXPIRED_TOKEN:-0}" \
    -v items="${ITEMS:-10}" \
    -v seed="${SEED:-0.42}" \
    < seed/seed.sql
