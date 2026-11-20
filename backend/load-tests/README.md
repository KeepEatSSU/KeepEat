# KeepEat 부하 테스트

k6는 애플리케이션 의존성이 아닌 외부 CLI 도구다. `build.gradle`이나 `package.json`에 추가하지 않고 Docker 이미지로 실행한다.

```text
load-tests/
├─ docker-compose.yml     측정 환경 (app·db·mock + k6, Prometheus·Grafana)
├─ prometheus.yml
├─ mock/ExpoMock.java     Expo Push API mock (의존성 없는 단일 파일)
├─ seed/                  시드 생성 (seed.sh, seed.sql)
├─ k6/tap-flow.js         배치 트리거 + 알림 탭 흐름 트래픽
├─ aggregate/             단계별 건수·도달률 집계
├─ run.sh                 측정 1회 실행
├─ restart.sh             재시작·DB 중지 제어
├─ smoke.sh               측정 도구 검증 (Smoke)
├─ smoke-health.js        API 연결 확인용 (아래 "API 부하 테스트")
└─ runs/                  실행 결과 (git 제외)
```

## 소비기한 알림 배치 측정

### 구성
| 서비스 | 이미지 | 리소스 | 역할 |
|---|---|---|---|
| app | eclipse-temurin:21-jre | 2 vCPU / 1GB, 힙 70% | `perf` 프로필. 정해진 시각 실행을 끄고 트리거로만 배치를 돌린다 |
| db | postgres:18 | 1 vCPU / 1GB | 매 실행 `down -v`로 초기화한다 |
| mock | eclipse-temurin:21-jdk | 1 vCPU / 512MB | `java ExpoMock.java`. 지연·장애를 흉내 내고 요청을 `runs/{runId}/mock.jsonl`에 남긴다 |
| k6 | grafana/k6:1.0.0 | 제한 없음 | `--profile tools`. 배치 트리거와 탭 흐름 트래픽 |
| prometheus, grafana | | 제한 없음 | `--profile monitoring`. 필요할 때만 켠다 |

- 앱 프로필은 `perf` **단독**이다. `local`은 재시작 때 스키마를 다시 만들어 재시작 시나리오가 성립하지 않는다.
- 외부 서비스 키는 더미 값이고 `.env`를 읽지 않는다. 메일 health 검사는 꺼져 있다(실제 SMTP 접속 방지).
- 로컬 수치는 절대 용량이 아니다. **같은 조건에서 개선 전후를 비교**하는 용도다.

### 준비
Docker, JDK 21(`./gradlew`를 호스트에서 실행한다), python3(스크립트가 시각 계산과 요약에 쓴다)가 필요하다. 측정할 코드로 jar를 만든다(개선 전후를 바꿀 때마다 다시 만든다).

```bash
cd backend
./gradlew bootJar
cp build/libs/backend-0.0.1-SNAPSHOT.jar build/libs/app.jar
cd load-tests
```

### 측정 도구 검증 (Smoke)
```bash
./smoke.sh
```
대상 10명(정상 7, 앱 끔 1, 토큰 없음 1, 만료 토큰 1)과 대상 아님 10명으로 돌리고, 집계가 정답지와 정확히 일치하는지 판정한다. 약 1~2분 걸린다. `SMOKE PASS`가 나와야 이후 결과를 믿을 수 있다.

### 측정 1회 실행
```bash
RUN_ID=L1-base-1 OPEN_RATE=0.144 MEDIAN_S=420 ./run.sh
```
초기화 → 시드 → mock 설정 → k6(트리거 + 탭 흐름) [+ 재시작] → receipt 대기 → 집계 순서로 돈다.

| 변수 | 기본값 | 뜻 |
|---|---|---|
| `USERS` | 10000 | 가입자 수 |
| `P_REGISTERED` | 0.5 | 재료를 등록한 비율 |
| `P_EXPIRING` | 0.3 | 등록 유저 중 3일 안에 만료되는 재료가 있는 비율 |
| `P_ALLOW` | 0.5 | 알림 허용(OS × 앱). 허용 안 함은 토큰 없음 / 앱 끔이 50:50 |
| `P_DDAY` | 0.33 | 임박 유저 중 D-day 비율 |
| `P_EXPIRED_TOKEN` | 0 | 토큰 보유 유저 중 만료 토큰 비율 |
| `ITEMS` | 10 | 등록 유저당 재료 수(임박 1~3개) |
| `SEED` | 0.42 | 난수 시드. 같으면 같은 데이터가 나온다 |
| `MOCK_CONFIG` | 없음 | mock 장애 설정. 아래 표 참고 |
| `MOCK_RECOVER` | 없음 | 트리거 `MOCK_RECOVER_AFTER_S`초(기본 60) 뒤 mock에 보낼 해제 설정. 예: `rate5xx=0` |
| `OPEN_RATE` | 0 | 받은 유저 대비 앱을 여는 비율. 0이면 트리거만 한다. 1을 넘으면 같은 유저가 여러 번 연다 |
| `MEDIAN_S` | 420 | 열기까지 지연 중앙값(초). 지수분포 |
| `CAP_S` | 10800 | 트래픽을 흘리는 최대 시간(판정 창 3시간) |
| `RESTART` | 없음 | `"<stop\|kill> <after-trigger\|after-last-send> <초>"` |
| `RESTART_SERVICE` | app | 내릴 서비스. DB 중지는 `db` |
| `DOWN_S` | 0 | 내린 뒤 다시 띄우기까지 기다릴 초 |
| `SETTLE_S` | 720 | 마지막 발송 뒤 receipt를 기다리는 최대 시간. ticket이 하나도 없으면(예: 장애가 끝까지 유지) 이 시간을 다 기다린 뒤 집계한다 |

**mock 장애 설정** (`MOCK_CONFIG`, 실행 중에는 `POST localhost:18089/__perf/config?키=값`)
| 키 | 기본값 | 뜻 |
|---|---|---|
| `p99Ms` | 575 | 지연 p99. 실측 분위수(p50 72 / p90 100 / p95 138 / p99 575ms) 사이를 선형 보간한다 |
| `rate5xx` | 0 | 500 응답 비율 |
| `rateTimeout` | 0 | 응답 없이 `timeoutMs` 기다렸다 끊는 비율(접수 안 됨) |
| `rateTimeoutAccepted` | 0 | 접수는 하고 응답만 `timeoutMs` 늦는 비율(앱 재시도 시 중복) |
| `rateDrop` | 0 | 응답 없이 연결을 닫는 비율 |
| `timeoutMs` | 6000 | 앱 응답 타임아웃(5초)보다 길게 둔다 |
| `force429Seconds` | – | 지금부터 N초 동안 모든 발송에 429 |
| `rateLimitPerSec` | 600 | 초당 한도. 넘으면 429 |
| `receiptMissingRate` | 0 | receipt가 끝까지 나오지 않는 비율 |
| `receiptDelayMs` | 1000 | ticket 발급 뒤 receipt가 조회되기까지 |

만료 토큰은 시드가 토큰 이름에 `expired`를 넣고, mock은 그 토큰에 `DeviceNotRegistered`를 돌려준다.

### 시나리오별 명령
**지켜야 할 것은 통제 하나다.** 개선 전후를 같은 명령·같은 시드·같은 리소스 제한으로 돌리고, 바뀌는 것은 jar뿐이어야 한다. 로컬은 결과가 흔들리므로 같은 조건을 3회 반복하고, 편차가 10%를 넘으면 5회 반복한다.

시나리오는 두 갈래다.
- **유실 재현**: 유실 지점(L1~L5)은 코드로 확인한 사실이라 확률을 훑지 않는다. 지점마다 사건이 확실히 일어나는 조합 하나로 재현하고, 개선 뒤 같은 명령으로 유실이 0이 되는지 본다. 장애는 한 번에 하나만 넣는다.
- **상한**: 배포 사양에서 어디까지 버티는지 단계적으로 올리며 찾는다. 개선안(keyset, 병렬 처리, 풀 크기 등)을 고르는 근거가 된다.

```bash
# 기준선: Load L1 기준 조합 (가입자 1만, 발송 약 750, 열람 14.4% · 중앙값 7분)
OPEN_RATE=0.144 MEDIAN_S=420 ./run.sh

# 유실 재현 — 재시작 (L3, L4). 종료 방식은 stop / kill 둘 다
RESTART="kill after-trigger 2" ./run.sh          # F1-a 알림 저장 중
RESTART="kill after-trigger 10" ./run.sh         # F1-b 큐가 가장 길 때(근사)
RESTART="kill after-last-send 60" ./run.sh       # F1-c 발송 끝, receipt 확인 전 (L4)

# 유실 재현 — 발송 3회 실패 (L2): 모든 요청이 5xx이다가 트리거 60초 뒤 Expo가 복구된다.
# 개선 전에는 재시도 3회를 1초 안에 소진해 복구 뒤에도 유실로 남는다. 개선 후에는 복구 뒤 재처리되어 유실 0이어야 한다
MOCK_CONFIG="rate5xx=1" MOCK_RECOVER="rate5xx=0" MOCK_RECOVER_AFTER_S=60 ./run.sh

# 중복 재현: Expo는 접수했는데 응답이 앱 타임아웃(5초)보다 늦다 → 앱 재시도마다 ticket이 새로 생긴다. 60초 뒤 정상화
MOCK_CONFIG="rateTimeoutAccepted=1" MOCK_RECOVER="rateTimeoutAccepted=0" MOCK_RECOVER_AFTER_S=60 ./run.sh

# 유실 재현 — receipt 조기 포기 (L5): receipt가 15분 뒤에 준비된다(Expo 권장 확인 시점)
MOCK_CONFIG="receiptDelayMs=900000" ./run.sh

# 상한 — ST1 발송 규모: Expo 요청 ≈ USERS × 0.075 (기본 비율). 500 → 1,000 → 2,000 → 5,000 → 1만 → 5만 → 10만
USERS=66700 ./run.sh                              # 대상 약 5,000명

# 상한 — ST4 동시 트래픽: 발송은 기준 조합 고정, 열람 14.4% → 100% → 2배 → 5배 → 10배, 지연 1분
OPEN_RATE=1 MEDIAN_S=60 ./run.sh
OPEN_RATE=10 MEDIAN_S=60 ./run.sh
```

- receipt가 앱이 조회를 그만둔 뒤(약 10분)에 준비되면, 그 유저는 `unverified_our_side`로 분류된다. Expo는 24시간 안에 조회하라고 권장하므로 이 미도달은 앱의 조기 포기(L5) 탓으로 본다. receipt 지연은 Expo 쪽 사건이지만, 분류는 "앱이 기다렸으면 확인됐는가"를 기준으로 한다.
- 그 밖의 장애(429 유지, receipt 누락, DB 중지, 만료 토큰, Expo 꼬리 지연)도 같은 변수로 돌릴 수 있다. 위 "mock 장애 설정"과 `RESTART_SERVICE`·`P_EXPIRED_TOKEN`을 참고한다.

Stress 중단 조건: ① 배치 시작 + 3시간 초과 ② 큐 1,000 + 스레드 16개 포화 ③ 오류율 5% 초과(API 포함) ④ 앱 다운·메모리 부족. 하나라도 나오면 다음 단계로 올리지 않는다. ②는 `--profile monitoring`으로 Prometheus를 켜고 `executor_queued_tasks{name="pushExecutor"}`, `executor_active_threads`, `hikaricp_connections_pending` 등으로 본다.

### 결과 읽기
`run.sh` 끝에 집계가 출력된다. 원자료는 `runs/{RUN_ID}/`(mock.jsonl, k6.log, k6-summary.json, restart.log)와 DB의 `loadtest` 스키마(`expected_user`, `user_result`)에 남는다. 다음 실행의 `down -v`가 DB를 지우므로, 필요한 값은 실행 직후 옮겨 둔다.

| 항목 | 뜻 |
|---|---|
| ① 알림 row 저장 | 임박 유저 중 오늘 알림이 저장된 유저. 차이 = L1 |
| ② Expo 요청 유저 | 앱 설정 켬 ∧ 토큰 있음 중 mock에 요청이 온 유저. 차이 = L3·L2 |
| ③ ticket ok | 도달률 분모 중 ticket을 받은 유저 |
| ④ receipt ok (도달) | 도달률 분모 중 receipt `ok`까지 확인된 유저 |
| 도달률 | ④ ÷ 분모. 분모 = 임박 ∧ 앱 켬 ∧ 토큰 있음 ∧ 만료 토큰 아님(사람 기준) |
| 미도달 원인 | `unverified_our_side`: receipt는 준비됐는데 앱이 조회하지 않음(L4 재시작, L5 조기 포기). `unverified_expo_side`: Expo가 receipt를 주지 않음 |
| 정확성 | 예상 밖 저장·요청, 중복(같은 알림에 ticket 2개 이상), 재시도 횟수, 지워지지 않은 만료 토큰 |
| 시간 | 트리거부터 마지막 ticket·마지막 D-day ticket까지(초). 판정 마감은 10800초(시작 + 3시간) |
| 탭 흐름 트래픽 | k6 요청 수, 실패율, 지연 분포 |

### 근사와 한계
- 탭 흐름 도착 곡선은 "전원이 트리거 시점에 받았다"고 근사한다. 발송이 길어지면 실제보다 앞쪽에 몰린다(비관적).
- 재시작 "큐가 가장 길 때"는 트리거 10초 뒤로, "마지막 발송"은 mock에 5초 동안 새 요청이 없을 때로 판단한다.
- 시드의 "오늘"은 KST 날짜다. 자정을 넘겨 실행하면 대상이 바뀐다.
- 연결 끊김은 RST가 아니라 응답 없이 닫기(EOF)다. 앱에서는 둘 다 같은 재시도 경로를 탄다.

## API 부하 테스트

### 사전 준비

- Docker Desktop 실행
- PostgreSQL 실행
- Spring Boot 애플리케이션을 8080 포트로 실행
- PowerShell 또는 IntelliJ 내장 터미널 사용

프로젝트 루트로 이동한다.

```powershell
cd .\backend
```

### k6 이미지 준비

최초 한 번만 이미지를 내려받는다.

```powershell
docker pull grafana/k6
```

정상 설치 확인:

```powershell
docker run --rm grafana/k6 version
```

이미지는 Docker Desktop 저장소에 유지된다. `--rm`은 테스트 종료 후 임시 컨테이너만 삭제하며 이미지와 프로젝트 파일은 삭제하지 않는다.

### Smoke 테스트 실행

먼저 Spring 서버의 liveness를 확인한다.

```powershell
curl.exe http://localhost:8080/actuator/health/liveness
```

예상 응답:

```json
{"status":"UP"}
```

프로젝트 루트에서 k6를 실행한다.

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -v "${PWD}\load-tests:/scripts:ro" `
  grafana/k6 run /scripts/smoke-health.js
```

한 줄 명령:

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -v "${PWD}\load-tests:/scripts:ro" grafana/k6 run /scripts/smoke-health.js
```

Spring은 Windows에서, k6는 Docker 컨테이너에서 실행되므로 k6에서는 `localhost` 대신 `host.docker.internal`을 사용한다.

세부 HTTP 지표가 필요하면 full summary로 실행한다.

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -v "${PWD}\load-tests:/scripts:ro" `
  grafana/k6 run --summary-mode=full /scripts/smoke-health.js
```

### 주요 지표 해석

| 지표 | 의미 | 확인할 내용 |
|---|---|---|
| `checks_succeeded` | 스크립트에 작성한 응답 검증 성공률 | 상태 코드와 응답 본문이 정상인지 확인 |
| `http_req_failed` | HTTP 요청 실패율 | 예상하지 않은 실패가 발생했는지 확인 |
| `http_req_duration` | 요청 전송부터 응답 전체 수신까지 걸린 시간 | 서버 응답 성능 확인 |
| `http_reqs` | 전체 요청 수와 실제 초당 요청 수 | 목표한 트래픽이 발생했는지 확인 |
| `iteration_duration` | 요청, check, `sleep`을 포함한 시나리오 한 바퀴의 시간 | 순수 HTTP 응답시간으로 해석하지 않음 |
| `iterations` | 완료된 시나리오 반복 수 | 사용자 흐름이 몇 번 완료됐는지 확인 |
| `vus` | 현재 동작 중인 가상 사용자 수 | 설정한 동시 사용자가 적용됐는지 확인 |
| `dropped_iterations` | 목표한 iteration을 시작하지 못한 수 | 서버 지연, VU 부족 또는 부하 생성기 포화 확인 |

응답시간은 평균만 보지 않고 다음 값을 함께 확인한다.

- `med` 또는 p50: 요청의 절반이 이 시간 이내에 완료
- p95: 요청의 95%가 이 시간 이내에 완료
- p99: 요청의 99%가 이 시간 이내에 완료
- `max`: 가장 느린 요청

요청 수가 매우 적으면 p95와 p99의 의미가 작다. 실제 성능 비교에서는 충분한 요청 수를 확보하고 같은 조건으로 여러 번 실행한다.

`sleep(1)`은 `iteration_duration`에는 포함되지만 `http_req_duration`에는 포함되지 않는다.

```text
http_req_duration   = HTTP 요청의 종단간 응답시간
iteration_duration  = HTTP 요청 + check + sleep + 스크립트 처리시간
```

Full summary에서 확인할 수 있는 HTTP 세부 지표:

- `http_req_connecting`: 서버 연결 시간
- `http_req_sending`: 요청 전송 시간
- `http_req_waiting`: 응답 첫 바이트를 기다린 시간
- `http_req_receiving`: 응답 데이터를 받은 시간

### 현재 확인된 내용

`smoke-health.js`를 통해 k6 컨테이너에서 Spring Boot 애플리케이션으로 요청을 보내고 응답을 검증하는 과정이 정상 동작함을 확인했다.

### 시나리오 추가 방향

처음에는 API별 단일 시나리오로 기준선을 만들고, 이후 실제 사용자 흐름을 섞은 시나리오를 추가한다.

예상 구조:

```text
load-tests/
├─ README.md
├─ smoke-health.js
├─ recipe-list.js
├─ recipe-search.js
├─ user-ingredients.js
├─ recipe-detail.js
└─ mixed-read.js
```

권장 진행 순서:

1. VU 1명으로 상태 코드, 인증, 응답 본문을 검증한다.
2. API 하나만 대상으로 VU 또는 RPS를 단계적으로 높인다.
3. p95, p99, 실패율, 실제 RPS를 기록한다.
4. Prometheus/Grafana에서 JVM, Tomcat, Hikari 지표를 함께 확인한다.
5. DB 병목이 의심되면 `pg_stat_statements`와 `EXPLAIN`을 확인한다.
6. 한 가지 개선만 적용하고 동일한 조건으로 재측정한다.
7. 단일 API 검증 후 여러 API를 섞은 사용자 흐름 테스트를 추가한다.

우선 추가할 대상:

```http
GET /api/v1/recipes?size=20
GET /api/v1/recipes?keyword={keyword}&size=20
GET /api/v1/user-ingredients
GET /api/v1/recipes/detail/{recipeId}
```

### 인증이 필요한 테스트

로그인을 매 iteration마다 수행하지 않는다. 테스트 전에 Access Token을 준비하고 환경변수로 전달한다.

```powershell
$env:ACCESS_TOKEN = "실제-access-token"
```

Docker 실행 시 현재 환경변수를 전달한다.

```powershell
-e ACCESS_TOKEN
```

스크립트에서는 다음처럼 사용한다.

```javascript
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

const params = {
    headers: {
        Authorization: `Bearer ${ACCESS_TOKEN}`,
    },
};
```

토큰과 계정 정보는 스크립트에 하드코딩하거나 Git에 커밋하지 않는다.

AI, OCR, 이메일, Push처럼 비용이나 외부 Rate Limit이 있는 API는 최초 부하 테스트 대상에서 제외한다.

### 테스트 결과 기록

각 테스트에서 최소한 다음 항목을 기록한다.

```text
대상 API:
데이터 규모:
VU 또는 목표 RPS:
테스트 시간:
Check 성공률:
HTTP 실패율:
실제 RPS:
p50 / p95 / p99:
서버 CPU / Heap / GC:
Hikari active / pending:
관찰 결과:
적용한 변경:
재테스트 결과:
```

로컬 테스트 결과는 운영 서버의 최대 수용량으로 해석하지 않는다. 동일한 로컬 환경과 데이터에서 개선 전후를 비교하는 용도로 사용한다.
