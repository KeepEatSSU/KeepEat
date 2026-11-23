-- 단계별 건수와 도달률 집계 (#52). aggregate.sh가 mock.jsonl을 loadtest.mock_event에 넣은 뒤 실행한다.
-- 정답지(loadtest.expected_user) · DB(notification, device_token) · mock 기록을 대조한다.
-- 변수: start_ms (배치 트리거 시각, epoch ms)

\set ON_ERROR_STOP on

DROP TABLE IF EXISTS loadtest.user_result;
CREATE TABLE loadtest.user_result AS
WITH ev AS (
    SELECT e->>'ev'                                              AS ev,
           (e->>'ts')::bigint                                    AS ts,
           substring(e->>'token' from '-u([0-9]+)\]')::bigint    AS uid,
           e->>'outcome'                                         AS outcome,
           e->>'ticket'                                          AS ticket,
           e->>'receipt'                                         AS receipt,
           (e->>'nid')::bigint                                   AS nid,
           e->>'collapse'                                        AS collapse
    FROM loadtest.mock_event
),
req AS (SELECT * FROM ev WHERE ev IN ('send', 'ticket')),
tk AS (SELECT * FROM ev WHERE ev = 'ticket' AND outcome IN ('ok', 'ok_late')),
receipt_ok AS (
    SELECT DISTINCT jsonb_array_elements_text(e->'okIds') AS ticket
    FROM loadtest.mock_event WHERE e->>'ev' = 'receipts'
),
notif AS (
    SELECT n.user_id, n.id AS notification_id
    FROM notification n
    WHERE n.dedupe_key LIKE 'EXPIRY_SOON:' || (SELECT max(seed_date) FROM loadtest.expected_user) || ':%'
)
SELECT x.*,
       x.expiring AND x.enabled AND x.has_token                           AS expect_request,
       x.expiring AND x.enabled AND x.has_token AND NOT x.expired_token   AS in_denominator, -- M2, M2'
       n.notification_id IS NOT NULL                                      AS saved,
       (SELECT count(*) FROM req r WHERE r.uid = x.user_id)               AS requests,
       (SELECT count(*) FROM req r WHERE r.uid = x.user_id
            AND r.outcome NOT IN ('ok', 'ok_late', 'DeviceNotRegistered')) AS failed_requests,
       (SELECT count(*) FROM tk WHERE tk.uid = x.user_id)                 AS ok_tickets,
       (SELECT count(DISTINCT tk.nid) FROM tk WHERE tk.uid = x.user_id)   AS ok_notifications,
       (SELECT count(DISTINCT tk.collapse) + count(*) FILTER (WHERE tk.collapse IS NULL)
            FROM tk WHERE tk.uid = x.user_id)                             AS shown_notifications,
       (SELECT count(*) FROM ev WHERE ev.uid = x.user_id AND ev.outcome = 'DeviceNotRegistered') AS dnr,
       EXISTS (SELECT 1 FROM tk JOIN receipt_ok o USING (ticket) WHERE tk.uid = x.user_id) AS reached,
       EXISTS (SELECT 1 FROM tk WHERE tk.uid = x.user_id AND tk.receipt = 'never')         AS receipt_never,
       (SELECT min(tk.ts) FROM tk WHERE tk.uid = x.user_id)               AS first_ok_ts,
       EXISTS (SELECT 1 FROM device_token d WHERE d.user_id = x.user_id)  AS token_left
FROM loadtest.expected_user x
LEFT JOIN notif n ON n.user_id = x.user_id;

\echo
\echo '== 단계별 건수 (기대 / 실제) =='
SELECT '① 알림 row 저장'   AS stage, count(*) FILTER (WHERE expiring)       AS expected, count(*) FILTER (WHERE saved)          AS actual FROM loadtest.user_result
UNION ALL
SELECT '② Expo 요청 유저',          count(*) FILTER (WHERE expect_request),            count(*) FILTER (WHERE requests > 0)                     FROM loadtest.user_result
UNION ALL
SELECT '③ ticket ok 유저',           count(*) FILTER (WHERE in_denominator),            count(*) FILTER (WHERE ok_tickets > 0)                   FROM loadtest.user_result
UNION ALL
SELECT '④ receipt ok 유저 (도달)',   count(*) FILTER (WHERE in_denominator),            count(*) FILTER (WHERE reached)                          FROM loadtest.user_result
UNION ALL
SELECT '   만료 토큰 (DNR, 분모 제외)', count(*) FILTER (WHERE expect_request AND expired_token), count(*) FILTER (WHERE dnr > 0)            FROM loadtest.user_result;

\echo '== 도달률 (M1: 사람 기준, 분모 = 임박 ∧ 앱 켬 ∧ 토큰 있음 ∧ 만료 토큰 아님) =='
SELECT count(*) FILTER (WHERE in_denominator)                                       AS denominator,
       count(*) FILTER (WHERE in_denominator AND reached)                           AS reached,
       round(100.0 * count(*) FILTER (WHERE in_denominator AND reached)
             / nullif(count(*) FILTER (WHERE in_denominator), 0), 3)                AS reach_pct,
       count(*) FILTER (WHERE in_denominator AND dday)                              AS dday_denominator,
       count(*) FILTER (WHERE in_denominator AND dday AND reached)                  AS dday_reached
FROM loadtest.user_result;

\echo '== 미도달 원인 (분모 안) =='
SELECT count(*) FILTER (WHERE NOT saved)                                         AS l1_not_saved,
       count(*) FILTER (WHERE saved AND requests = 0)                            AS l3_no_request,
       count(*) FILTER (WHERE requests > 0 AND ok_tickets = 0)                   AS l2_send_failed,
       count(*) FILTER (WHERE ok_tickets > 0 AND NOT reached AND receipt_never)  AS unverified_expo_side,
       count(*) FILTER (WHERE ok_tickets > 0 AND NOT reached AND NOT receipt_never) AS unverified_our_side
FROM loadtest.user_result WHERE in_denominator AND NOT reached;

\echo '== 정확성·부수 효과 =='
SELECT count(*) FILTER (WHERE saved AND NOT expiring)                   AS unexpected_saved,
       count(*) FILTER (WHERE requests > 0 AND NOT expect_request)      AS unexpected_requests,
       count(*) FILTER (WHERE ok_tickets > ok_notifications)            AS duplicate_users,
       count(*) FILTER (WHERE shown_notifications > ok_notifications)   AS shown_duplicate_users,
       coalesce(sum(requests - 1) FILTER (WHERE requests > 1), 0)       AS retries,
       count(*) FILTER (WHERE expect_request AND expired_token AND token_left)           AS expired_token_not_deleted
FROM loadtest.user_result;

\echo '== 시간 (배치 트리거 기준, 초) =='
SELECT round((max(first_ok_ts) - :start_ms) / 1000.0, 3)                         AS last_ok_ticket_s,
       round((max(first_ok_ts) FILTER (WHERE dday) - :start_ms) / 1000.0, 3)     AS last_dday_ok_ticket_s,
       10800                                                                    AS deadline_s -- S5: 시작 + 3시간
FROM loadtest.user_result WHERE in_denominator;
