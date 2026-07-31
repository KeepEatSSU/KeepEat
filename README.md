# KeepEat Backend 개발 환경 세팅 가이드

본 가이드는 KeepEat 백엔드 서버의 로컬 개발 환경을 구축하기 위한 문서입니다.

## 1. 필수 사전 설치
* Git
* Docker Desktop (로컬 DB 실행용)
* IntelliJ IDEA

## 2. 프로젝트 클론 및 브랜치 설정
본 프로젝트의 개발 브랜치는 `dev`이며, 백엔드 작업은 `BE` 브랜치에서 진행합니다.

```bash
# 프로젝트 클론
git clone https://github.com/KeepEatSSU/KeepEat.git
cd KeepEat

# 최신 변경사항 동기화 및 dev 브랜치 이동
git fetch origin
git switch dev

# 백엔드 통합 브랜치로 이동
git switch BE
```

## 3. Java SDK 및 IDE 설정
* Java 버전: Eclipse Temurin 21.0.10
* 설정 방법 (IntelliJ):
    1. 프로젝트를 IntelliJ로 엽니다.
    2. File > Project Structure (단축키: Cmd/Ctrl + ;)로 이동합니다.
    3. Project 탭에서 SDK를 설정합니다.
    4. 목록에 없다면 Add SDK > Download JDK를 클릭하고, Version은 21, Vendor는 Eclipse Temurin을 선택하여 다운로드 및 적용합니다.

## 4. 애플리케이션 설정과 프로필

`backend/src/main/resources`의 설정 파일은 모두 저장소에서 관리합니다. 별도의
`application-local.yml` 또는 `application-prod.yml`을 만들거나 외부에서 받아 덮어쓰지
마세요.

| 설정 파일 / 프로필 | 역할 |
|---|---|
| `application.yml` | DB 연결, OAuth, 메일, JWT, 외부 API, 로깅, Actuator 등 모든 환경의 공통 설정 |
| `application-local.yml` / `local` | 로컬 개발용 JPA·SQL 초기화 설정. 활성 프로필을 지정하지 않으면 기본 적용 |
| `application-prod.yml` / `prod` | 운영용 JPA, 메일 타임아웃, 보안 쿠키 설정만 공통 설정 위에 추가 |
| `application-demo.yml` / `demo` | 데모 DB의 SSL 연결과 Swagger 인증 설정 추가 |
| `application-tls.yml` / `tls` | Spring이 HTTPS를 직접 종료하는 데모 서버용 TLS 설정 |

DB 설정은 프로필마다 별도 변수명을 사용하지 않습니다. 모든 환경에서
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`를 사용하며, 데모와 운영
배포 워크플로가 각각 `DEMO_DB_*`, `PROD_DB_*` GitHub Secrets를 이 `DB_*` 이름으로
매핑합니다.

## 5. 로컬 환경변수와 PostgreSQL 실행

보안을 위해 실제 `.env`는 저장소에 포함하지 않습니다. 저장소 루트에서 다음과 같이
예제 파일을 복사한 뒤, 비어 있는 값을 로컬 개발용 값으로 채웁니다.

```bash
cd backend
cp .env.example .env
```

`backend/.env`는 애플리케이션과 `backend/docker-compose.yml`이 함께 사용하는
파일입니다. 로컬 JVM에서 실행할 때는 예제의 `DB_HOST=localhost`,
`DB_PORT=5433`을 유지하면 됩니다. `DB_NAME`, `DB_USER`, `DB_PASSWORD`는
PostgreSQL 컨테이너 생성 값과 Spring의 접속 값에 동일하게 전달됩니다.

로컬 DB 서비스에는 Compose의 `local` 프로필이 지정되어 있으므로 서비스 이름까지
명시해 실행합니다.

```bash
docker compose --env-file .env --profile local config --quiet
docker compose --env-file .env --profile local up -d db
docker compose --env-file .env --profile local ps
```

정상적으로 실행되면 PostgreSQL은 `localhost:${DB_PORT}`(예제 기본값 `5433`)에서
접속할 수 있습니다. 종료할 때는 아래 명령을 사용합니다. 데이터는
`keepeat-db-data` 볼륨에 유지됩니다.

```bash
docker compose --env-file .env --profile local down
```

프로필 없이 `docker compose up -d`를 실행하면 로컬 DB가 아니라 배포용 `app`
서비스가 선택됩니다. 로컬 개발에서는 위와 같이 `--profile local ... db`를 사용하세요.

## 6. 애플리케이션 실행

### IntelliJ에서 실행

Spring Boot는 `.env` 파일을 자동으로 읽지 않으므로 IntelliJ에서는 EnvFile
플러그인을 사용합니다.

1. IntelliJ Preferences(Settings) > Plugins에서 `EnvFile`을 설치하고 IDE를 재시작합니다.
2. Run/Debug Configurations > Edit Configurations에서 `BackendApplication` 실행 구성을 엽니다.
3. EnvFile 탭에서 `Enable EnvFile`을 체크합니다.
4. `backend/.env`를 추가합니다.
5. 활성 프로필은 비워 두거나 `local`로 지정한 뒤 실행합니다.

`application.yml`의 `spring.profiles.default`가 `local`이므로 프로필을 생략해도
로컬 설정이 적용됩니다.

### 터미널에서 실행

저장소 루트에서 다음 명령을 실행하면 `.env`를 Spring 설정으로 불러와 기본
`local` 프로필로 서버를 시작합니다.

```bash
cd backend
./gradlew bootRun --args='--spring.config.import=optional:file:.env[.properties]'
```

기동 후 상태를 확인합니다.

```bash
curl --fail http://localhost:8080/actuator/health
```

로컬 모니터링까지 실행하려면
[`backend/monitoring/README.md`](backend/monitoring/README.md)를 참고하세요.

## 7. 배포용 Compose와 프로필

`backend/docker-compose.yml`의 두 서비스는 용도가 다릅니다.

- `db`: 로컬 개발용이며 `local` Compose 프로필에서만 실행됩니다.
- `app`: 배포 번들의 `app.jar`를 이미지로 만들고 `.env`를 컨테이너에 전달합니다.
  `SPRING_PROFILES_ACTIVE`가 없으면 `prod`를 사용합니다.

운영 배포는 서버에서 다음 명령을 사용하고, 데모 배포는 TLS 포트·볼륨을 추가하는
Compose 파일과 `demo,tls` 프로필을 함께 사용합니다.

```bash
# 운영 배포 번들
docker compose up -d --build app

# 데모 배포 번들
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build app
```

위 명령은 GitHub Actions가 `app.jar`, Dockerfile, Compose 파일, `entrypoint.sh`를
모아 둔 **배포 서버용 번들**을 기준으로 합니다. 소스만 클론한 로컬 환경에서 DB를
실행하는 명령과 혼동하지 마세요.

## 8. 브랜치 규칙 및 협업 가이드

백엔드 파트의 신규 작업은 항상 `BE` 브랜치를 기준으로 파생시킵니다.

* 기능 브랜치 생성:
  ```bash
  git switch BE
  git switch -c feature/BE/작업할내용
  # 예: git switch -c feature/BE/login
  ```

* 원활한 프로젝트 진행과 일관성 있는 코드 품질을 위해 자체적인 컨벤션을 지키고자 합니다.
  자세한 규칙은 아래 노션 워크스페이스에서 확인하실 수 있습니다.
- [Git 및 코드 컨벤션 (Notion)](https://www.notion.so/git-convention-1eef5f52a1d783eda7f381b7ec9d6e08?source=copy_link)
