# 모니터링 (Prometheus + Grafana)

KeepEat 백엔드의 로컬 모니터링 스택입니다.
스프링 서버의 상태(CPU·메모리·DB 커넥션·HTTP 요청 등)를 지표로 수집해 그래프로 봅니다.

## 구조

```
[Spring + Actuator]  ──(15초마다 긁어감)──▶  [Prometheus]  ──(조회)──▶  [Grafana]
  /actuator/prometheus                        수집·저장(TSDB)            시각화(대시보드)
   (지표를 텍스트로 노출)                        localhost:9090            localhost:3000
```

- **Actuator** : 스프링이 자기 지표를 Prometheus 형식으로 노출 (`/actuator/prometheus`)
- **Prometheus** : 그 지표를 주기적으로 긁어와 시계열 DB에 저장 (도커 컨테이너)
- **Grafana** : Prometheus 데이터를 대시보드로 시각화 (도커 컨테이너)

## 사전 준비

- Docker Desktop 실행 중
- `backend/.env.example`을 복사해 만든 `backend/.env`의 필수 값 설정 완료
- 스프링 서버가 로컬 `8080` 포트에서 `local` 프로필로 실행 중

## 최초 세팅

### 1. 로컬 실행에서 Prometheus 지표 노출

`application.yml`과 `application-local.yml`은 모두 저장소에서 관리하므로 새로 만들거나
덮어쓰지 않습니다. 공통 `application.yml`은 기본적으로 `health` 엔드포인트만
노출합니다. 로컬에서 모니터링할 때만 다음 환경변수로 노출 범위를 확장합니다.

```dotenv
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus
```

`backend/.env.example`에도 같은 항목이 주석으로 들어 있습니다. IntelliJ EnvFile을
사용한다면 `backend/.env`에서 해당 줄의 주석을 해제한 뒤 서버를 재시작하세요.

터미널에서 일회성으로 실행할 때는 저장소 루트에서 다음 명령을 사용할 수 있습니다.

```bash
cd backend
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus \
  ./gradlew bootRun --args='--spring.config.import=optional:file:.env[.properties]'
```

Micrometer Prometheus 의존성과 SecurityConfig의 `/actuator/prometheus` 허용은
이미 설정되어 있습니다. 아래 명령에서 지표 텍스트가 나오면 준비 완료입니다.

```bash
curl --fail http://localhost:8080/actuator/prometheus
```

### 2. 모니터링 스택 실행

저장소 루트의 새 터미널에서 실행합니다.

```bash
cd backend/monitoring
docker compose up -d
```

- Prometheus : http://localhost:9090
- Grafana    : http://localhost:3000

Prometheus가 스프링을 잘 긁는지 확인 : `9090 → Status → Targets` 에서
`keepeat-backend` 가 **UP** 이면 정상.

### 3. Grafana 설정 (최초 1회)

1. `http://localhost:3000` 접속, 로그인 `admin` / `admin`
2. **Connections → Data sources → Add data source → Prometheus**
   - URL : `http://prometheus:9090`  ← `localhost` 아님 (같은 도커 네트워크의 서비스명)
   - **Save & test** 초록 확인
3. **Dashboards → New → Import**
   - Dashboard ID : `19004` (Spring Boot / Micrometer)
   - 데이터소스 : Prometheus 선택 → Import

## 실행 / 종료 명령

아래 명령은 모두 `backend/monitoring`에서 실행합니다.

| 상황 | 명령 |
|------|------|
| 켜기 | `docker compose up -d` |
| 잠깐 끄기 (설정 유지) | `docker compose stop` |
| 다시 켜기 | `docker compose start` |
| 컨테이너 삭제 (설정 유지) | `docker compose down` |
| **전체 초기화 (설정·데이터 삭제)** | `docker compose down -v` ⚠️ |

- Grafana 설정(데이터소스·대시보드·비번)과 지표 데이터는 도커 볼륨
  (`grafana-data`, `prometheus-data`)에 저장됩니다.
- `-v` 옵션은 이 볼륨까지 지우므로 Grafana를 처음부터 다시 세팅해야 합니다. 평소엔 쓰지 마세요.

## 파일 설명

- `docker-compose.yml` : Prometheus + Grafana 컨테이너 정의
- `prometheus.yml` : Prometheus가 긁어올 대상(스프링) 설정

## 참고

- 로컬 전용 구성입니다. Grafana 기본 비밀번호(`admin/admin`)는 로컬 기준값입니다.
- 이 폴더의 Compose는 Prometheus와 Grafana만 실행합니다. 로컬 PostgreSQL은
  `backend`에서 `docker compose --env-file .env --profile local up -d db`로
  별도 실행합니다.
- 운영 배포 시에는 지표 엔드포인트를 관리 포트 분리 + 방화벽(사설망)으로
  보호하고, Grafana 비밀번호를 변경해야 합니다.

## 운영 배포 기준

현재 모니터링 Compose는 로컬 전용입니다. 운영으로 확장할 때도 별도
`application-prod.yml`을 생성하거나 공통 설정을 복사하지 않고, 저장소의
`application.yml` + `prod` 프로필에 운영 환경변수를 주입합니다.

백엔드 `app` 서비스는 `.env`를 읽고 `SPRING_PROFILES_ACTIVE`가 없으면 `prod`를
활성화합니다. DB는 공통 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`,
`DB_PASSWORD`를 사용하며, 배포 워크플로가 `PROD_DB_*` GitHub Secrets를 이
이름으로 매핑합니다. 모니터링 서버의 Compose는 백엔드 `.env`나 DB 변수를
사용하지 않습니다.

### 1. 배치 구조 — 모니터링 전용 서버 분리

```
[인스턴스 A: API 서버]              [인스턴스 B: 모니터링 서버]
  Spring + Actuator   ◀───(사설망으로 긁어감)───  Prometheus + Grafana
```

- 모니터링(Prometheus + Grafana)을 **API 서버와 분리된 별도 인스턴스**에 둔다.
- 이유:
  - 모니터링이 API 서버 자원을 잠식하지 않음 (부하테스트 측정 오염 방지)
  - API 서버가 죽어도 모니터링은 살아있어 장애 원인 추적 가능
- 규모가 작으면 대안으로 **Grafana Cloud 무료 티어**(관리형)를 써서
  인스턴스 관리 없이 저장·시각화를 위임할 수도 있다.

### 2. 지표 엔드포인트 보안

로컬처럼 `/actuator/prometheus`를 공개하면 안 되므로 아래를 적용한다.

- **관리 포트 분리**: 운영 배포 환경에 다음 값을 주입해 Actuator를 API
  포트(8080)와 분리한다.

  ```dotenv
  MANAGEMENT_SERVER_PORT=9091
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus
  ```

  사용자용 8080에서는 `/actuator/prometheus`가 노출되지 않고, 지표는 9091에서
  제공된다. 현재 백엔드 Compose는 `8080:8080`만 게시하므로, 운영에 적용할 때는
  `app` 서비스의 9091 포트를 사설 인터페이스나 내부 Docker 네트워크에만
  연결하도록 Compose 구성도 함께 변경해야 한다.
- **네트워크 격리**: 9091(관리 포트)은 방화벽/보안그룹에서 인터넷 차단,
  사설망(모니터링 서버)에서만 접근 허용. 인터넷에서는 도달 경로 자체가 없어진다.
- 필요 시 **basic auth**를 걸고 Prometheus에 계정을 설정한다.

### 3. 프로파일별 설정

- 공통 `application.yml`은 계속 `health`만 노출하고, 운영 모니터링 배포에서만
  위 `MANAGEMENT_*` 환경변수를 주입한다.
- 현재 SecurityConfig는 `/actuator/prometheus`를 `permitAll`하므로 운영에서는
  관리 포트의 사설망 격리를 필수로 적용하고, 필요하면 별도 인증 구성을 추가한다.

### 4. 배포 시 설정 파일 재사용

- 이 폴더의 `docker-compose.yml`, `prometheus.yml`을 모니터링 서버에 옮기고,
  `prometheus.yml`의 `targets`를 `host.docker.internal:8080`에서
  **API 서버의 사설 IP:9091**로 바꾼다.
- Grafana 비밀번호는 환경변수/시크릿으로 주입하고 기본값(`admin/admin`)을 제거한다.

### 5. 알림(Alerting) 도입

- 운영에서는 대시보드를 상시 감시하지 않고, 임계치 초과 시 알림을 받는다.
  - 예: CPU 90%↑, 에러율(5xx) 급증, 힙 메모리 포화, AI 처리 큐 적체 등
  - Grafana Alerting 또는 Prometheus Alertmanager → Slack/Discord/메일 통지
- 대시보드는 "알림이 오면 원인을 진단하는 도구"로 사용한다.
