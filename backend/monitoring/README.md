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
- 스프링 서버가 로컬 `8080` 포트로 실행 중 (`local` 프로파일)

## 최초 세팅

### 1. 스프링 지표 노출 설정 (`application-local.yml`)

> `application-local.yml`은 `.gitignore` 대상이라 레포에 없습니다.<br>
> 각자 로컬 파일에 아래 설정을 직접 추가해야 `/actuator/prometheus`가 열립니다.<br>
> 디스코드에 application-local.yml 파일 올려둘게요. (로깅 관련 설정도 있어요 이거랑은 별개임)



의존성(`io.micrometer:micrometer-registry-prometheus`)과 SecurityConfig의
`/actuator/prometheus` 허용은 이미 커밋되어 있습니다.

설정 후 스프링을 재시작하고, 브라우저에서 `http://localhost:8080/actuator/prometheus`
에 지표 텍스트가 나오면 준비 완료입니다.

### 2. 모니터링 스택 실행

```bash
cd monitoring
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
- 운영 배포 시에는 지표 엔드포인트를 관리 포트 분리 + 방화벽(사설망)으로
  보호하고, Grafana 비밀번호를 변경해야 합니다.

## 추후 계획 (운영 배포)

현재는 로컬 전용이며, 실제 운영에서는 아래와 같이 확장한다.

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

- **관리 포트 분리** : actuator를 API 포트(8080)와 다른 포트로 노출
  ```yaml
  management:
    server:
      port: 9091
  ```
  → 사용자용 8080에서는 `/actuator/prometheus`가 404가 되고,
    지표는 9091로만 노출된다.
- **네트워크 격리** : 9091(관리 포트)은 방화벽/보안그룹에서 인터넷 차단,
  사설망(모니터링 서버)에서만 접근 허용. 인터넷에서는 도달 경로 자체가 없어진다.
- 필요 시 **basic auth** 를 걸고 Prometheus에 계정을 설정 (네트워크 격리 + 인증 이중 방어).

### 3. 프로파일별 설정

- `application-prod.yml` 에서 노출 범위를 지표 수집에 필요한 최소한으로 관리
  (`management.endpoints.web.exposure.include`).
- 운영에서 `/actuator/prometheus`를 열 경우, SecurityConfig도 프로파일별로
  분리해 로컬처럼 무조건 `permitAll` 하지 않도록 정리하는 것을 고려.

### 4. 배포 시 설정 파일 재사용

- 이 폴더의 `docker-compose.yml`, `prometheus.yml` 을 모니터링 서버에 그대로 옮기고,
  `prometheus.yml`의 `targets`를 `host.docker.internal:8080`에서
  **API 서버의 사설 IP:포트**로 바꾸면 된다.
- Grafana 비밀번호는 환경변수/시크릿으로 주입하고 기본값(`admin/admin`)을 제거한다.

### 5. 알림(Alerting) 도입

- 운영에서는 대시보드를 상시 감시하지 않고, 임계치 초과 시 알림을 받는다.
  - 예: CPU 90%↑, 에러율(5xx) 급증, 힙 메모리 포화, AI 처리 큐 적체 등
  - Grafana Alerting 또는 Prometheus Alertmanager → Slack/Discord/메일 통지
- 대시보드는 "알림이 오면 원인을 진단하는 도구"로 사용한다.
