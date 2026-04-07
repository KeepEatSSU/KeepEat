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

## 4. 로컬 DB 세팅 및 환경변수 설정
보안을 위해 `.env` 파일은 레포지토리에 포함하지 않으며, 로컬 환경에서 직접 생성해야 합니다.

### 4-1. .env 파일 생성
프로젝트 루트 경로(docker-compose.yml이 위치한 경로)에 `.env` 파일을 생성하고 자세한 내용은 Discord의 자료 공유방을 올려뒀습니다.
(주의: 해당 파일은 .gitignore에 등록되어 있으므로 강제로 git add 하지 마세요.)

### 4-2. Docker 컨테이너 실행
터미널에서 `.env` 파일이 있는 경로로 이동한 뒤 아래 명령어를 실행하여 PostgreSQL DB를 구동합니다.
```bash
docker-compose up -d
```
정상적으로 실행 시 `localhost:5433` 포트로 DB 인스턴스가 실행됩니다.

## 5. IntelliJ EnvFile 플러그인 설정
스프링 부트 애플리케이션이 `.env` 파일을 읽을 수 있도록 플러그인 설정이 필요합니다.

1. IntelliJ Preferences(Settings) > Plugins에서 'EnvFile'을 검색하여 설치 후 IDE를 재시작합니다.
2. 우측 상단의 Run/Debug Configurations > Edit Configurations...를 클릭합니다.
3. Spring Boot 실행 환경(Application)을 선택합니다.
4. EnvFile 탭으로 이동하여 'Enable EnvFile'을 체크합니다.
5. 하단의 '+' 버튼을 누르고 '.env file'을 선택한 뒤, 생성해둔 `.env` 파일 경로를 지정합니다.
6. Apply > OK를 클릭하여 저장합니다.

## 6. 서버 실행 확인
서버를 실행하기 위해 src/main/resources에서 application-local.yml과 application-prod.yml을 생성해주세요. 이 또한 Discord의 자료 공유방에 올려뒀습니다.
IntelliJ에서 BackendApplication을 실행하여 콘솔에 에러 로그 없이 기동되는지 확인합니다.

## 7. 브랜치 규칙 및 협업 가이드
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
