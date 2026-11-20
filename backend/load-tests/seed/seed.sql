-- 소비기한 알림 배치 시드 (#52). seed.sh가 psql 변수를 넘겨 실행한다.
-- 빈 DB(Flyway 스키마 + db/seed 마스터)에서만 실행한다. 같은 seed 값이면 같은 데이터가 나온다.
--
-- 변수
--   users            가입자 수
--   p_registered     재료를 등록해 둔 비율
--   p_expiring       등록 유저 중 3일 안에 만료되는 재료가 있는 비율
--   p_allow          알림 허용 비율 (OS × 앱 설정). 허용 안 함은 OS 거부(토큰 없음) / 앱 끔을 50:50 (S10)
--   p_dday           임박 유저 중 D-day 재료가 있는 비율
--   p_expired_token  토큰 보유 유저 중 만료 토큰 비율 (F7)
--   items            등록 유저당 재료 수. 임박 유저는 그중 1~3개가 임박 (S11)
--   seed             난수 시드 (-1.0 ~ 1.0)

\set ON_ERROR_STOP on
SET client_min_messages = warning;
\o /dev/null
SET max_parallel_workers_per_gather = 0; -- 병렬 실행이면 random() 순서가 바뀌어 재현이 깨진다

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM app_user) THEN
        RAISE EXCEPTION 'app_user가 비어 있지 않다. docker compose down -v로 초기화한 뒤 실행한다';
    END IF;
END $$;

SELECT setseed(:seed);

CREATE TEMP TABLE seed_user AS
SELECT id,
       registered,
       registered AND r2 < :p_expiring                         AS expiring,
       allowed,
       NOT allowed AND r4 >= 0.5                               AS app_off,
       -- 앱 끔 유저도 OS 권한은 있으므로 토큰은 있다. OS 거부 유저만 토큰이 없다
       allowed OR (NOT allowed AND r4 >= 0.5)                  AS has_token,
       r5, r6, r7
FROM (SELECT id,
             r1 < :p_registered AS registered,
             r2,
             r3 < :p_allow      AS allowed,
             r4, r5, r6, r7
      FROM (SELECT id, random() r1, random() r2, random() r3, random() r4, random() r5, random() r6, random() r7
            FROM generate_series(1, :users) id) raw) x;

ALTER TABLE seed_user ADD COLUMN dday boolean, ADD COLUMN expired_token boolean, ADD COLUMN n_expiring int;
UPDATE seed_user SET dday          = expiring AND r5 < :p_dday,
                     expired_token = has_token AND r6 < :p_expired_token,
                     n_expiring    = CASE WHEN expiring THEN 1 + floor(r7 * 3)::int ELSE 0 END;

-- 앱 테이블
INSERT INTO app_user (id, email, nickname, role, is_notification_enabled)
SELECT id, 'perf-u' || id || '@loadtest.local', 'perf' || id, 'ROLE_USER', NOT app_off
FROM seed_user ORDER BY id;

INSERT INTO device_token (user_id, token, created_at)
SELECT id,
       CASE WHEN expired_token THEN 'ExponentPushToken[expired-u' || id || ']'
            ELSE 'ExponentPushToken[perf-u' || id || ']' END,
       now()
FROM seed_user WHERE has_token ORDER BY id;

-- 재료: 앞쪽 n_expiring개는 임박(오늘~3일 뒤), 나머지는 4~30일 뒤.
-- D-day 유저는 첫 재료를 오늘로 둔다. D-day가 아닌 임박 유저는 1~3일 뒤만 쓴다.
WITH today AS (SELECT (now() AT TIME ZONE 'Asia/Seoul')::date AS d),
     ing AS (SELECT array_agg(id ORDER BY id) AS ids FROM ingredient)
INSERT INTO user_ingredient (user_id, ingredient_id, expiry_date, purchase_date, quantity, unit, storage_type)
SELECT u.id,
       ing.ids[1 + floor(random() * array_length(ing.ids, 1))::int],
       today.d + CASE
           WHEN k <= u.n_expiring AND u.dday AND k = 1 THEN 0
           WHEN k <= u.n_expiring AND u.dday         THEN floor(random() * 4)::int
           WHEN k <= u.n_expiring                    THEN 1 + floor(random() * 3)::int
           ELSE 4 + floor(random() * 27)::int
       END,
       today.d - 3,
       1, '개', '냉장'
FROM seed_user u
CROSS JOIN generate_series(1, :items) k
CROSS JOIN today
CROSS JOIN ing
WHERE u.registered
ORDER BY u.id, k;

-- 세션: k6가 perf 전용 키로 서명한 JWT(sid)를 인증 필터가 통과시키도록 둔다. 로그인 API는 bcrypt 비용 때문에 쓰지 않는다
INSERT INTO user_session (id, user_id, refresh_token_hash, created_at, expires_at)
SELECT 'perf-s-' || id, id, 'perf-' || id, now(), now() + interval '2 days'
FROM seed_user ORDER BY id;

SELECT setval('app_user_id_seq', (SELECT max(id) FROM app_user));

-- 정답지: 앱이 만료 토큰을 지워도 남도록 별도 스키마에 둔다.
-- 스키마 이름을 DB 사용자(perf)와 같게 두면 search_path("$user", public) 때문에 Flyway가 그 스키마를 기본으로 잡아 재기동이 실패한다
CREATE SCHEMA IF NOT EXISTS loadtest;
DROP TABLE IF EXISTS loadtest.expected_user;
CREATE TABLE loadtest.expected_user AS
SELECT id AS user_id, registered, expiring, dday, NOT app_off AS enabled, has_token, expired_token, n_expiring,
       (now() AT TIME ZONE 'Asia/Seoul')::date AS seed_date
FROM seed_user;
ALTER TABLE loadtest.expected_user ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS loadtest.seed_params;
CREATE TABLE loadtest.seed_params AS
SELECT :users AS users, :p_registered::float AS p_registered, :p_expiring::float AS p_expiring,
       :p_allow::float AS p_allow, :p_dday::float AS p_dday, :p_expired_token::float AS p_expired_token,
       :items AS items, :seed::float AS seed, now() AS seeded_at;

\o
-- 요약
SELECT count(*)                                                       AS users,
       count(*) FILTER (WHERE expiring)                               AS expiring_users,
       count(*) FILTER (WHERE expiring AND enabled)                   AS push_jobs,
       count(*) FILTER (WHERE expiring AND enabled AND has_token)     AS expo_requests,
       count(*) FILTER (WHERE expiring AND enabled AND has_token AND NOT expired_token) AS deliverable,
       count(*) FILTER (WHERE dday AND enabled AND has_token)         AS dday_targets
FROM loadtest.expected_user;
