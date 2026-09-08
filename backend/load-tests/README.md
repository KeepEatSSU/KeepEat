# KeepEat 부하 테스트

KeepEat 백엔드의 k6 부하 테스트 스크립트와 실행 방법을 관리한다.

k6는 애플리케이션 의존성이 아닌 외부 CLI 도구다. `build.gradle`이나 `package.json`에 추가하지 않고, Docker 이미지로 실행하며 테스트 스크립트만 이 폴더에서 Git으로 관리한다.

## 현재 구성

```text
load-tests/
├─ README.md
└─ smoke-health.js
```

- `smoke-health.js`: `/actuator/health/liveness` 연결 확인용 Smoke 테스트
- 실제 비즈니스 API 테스트는 이후 시나리오별로 추가한다.

## 사전 준비

- Docker Desktop 실행
- PostgreSQL 실행
- Spring Boot 애플리케이션을 8080 포트로 실행
- PowerShell 또는 IntelliJ 내장 터미널 사용

프로젝트 루트로 이동한다.

```powershell
cd .\backend
```

## k6 이미지 준비

최초 한 번만 이미지를 내려받는다.

```powershell
docker pull grafana/k6
```

정상 설치 확인:

```powershell
docker run --rm grafana/k6 version
```

이미지는 Docker Desktop 저장소에 유지된다. `--rm`은 테스트 종료 후 임시 컨테이너만 삭제하며 이미지와 프로젝트 파일은 삭제하지 않는다.

## Smoke 테스트 실행

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

## 주요 지표 해석

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

## 현재 확인된 내용

`smoke-health.js`를 통해 k6 컨테이너에서 Spring Boot 애플리케이션으로 요청을 보내고 응답을 검증하는 과정이 정상 동작함을 확인했다.

## 시나리오 추가 방향

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

## 인증이 필요한 테스트

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

## 테스트 결과 기록

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
