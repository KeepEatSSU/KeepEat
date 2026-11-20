# KeepEat 백엔드 에이전트 작업 가이드

이 문서는 `backend/` 기준이다. 명령어는 이 디렉터리에서 실행한다.

## 스택
- Spring Boot 4 · Java 21 · Gradle · PostgreSQL 18 · JPA · Flyway
- 인증: JWT + Google OAuth2 · AI: Spring AI(Google GenAI), OpenAI(OCR) · 푸시: Expo

## 명령어
- 빌드와 테스트: `./gradlew build`
- 테스트만: `./gradlew test`
  - Docker만 실행 중이면 된다. 테스트 DB(postgres:18)는 Testcontainers가 자동으로 띄우고 끝나면 정리한다

## 프로필
| 프로필 | 스키마 | 비고 |
|---|---|---|
| local | Flyway 마이그레이션 + 마스터 데이터(`db/seed`) + `validate` | 재시작해도 데이터가 유지된다 |
| test | Flyway 마이그레이션 + `validate` | 마이그레이션이 빠지면 테스트 실패 |
| demo / prod | Flyway + `validate` | `update` 금지 |
| perf | Flyway + 스키마 유지 (단독으로 켬, `load-tests/docker-compose.yml`) | 알림 배치 측정 전용. 재시작 시나리오에서 데이터가 남아야 하므로 local과 섞지 않는다. 정해진 시각 실행을 끄고, Expo 주소를 mock으로 바꾸고, `POST /api/internal/perf/expiry-batch` 트리거와 Prometheus 지표를 연다 |

## 구조와 규칙
- 패키지: `com.keepeat.backend.domain.<도메인>`, 계층은 Controller → Service → Repository → Entity
- 예외는 `KeepEatException(ErrorCode)`로 던진다. 에러 응답 구조는 개편이 예정되어 있다
- Enum 컬럼은 반드시 `@Enumerated(EnumType.STRING)`
- 날짜 기준은 `Asia/Seoul`이다
- 외부 API(AI, Expo) 호출은 DB 트랜잭션 밖에서 한다

## DB 변경
- 엔티티를 바꾸면 `src/main/resources/db/migration/V{n}__설명.sql`을 함께 추가한다
- **이미 적용된 마이그레이션 파일은 수정하지 않는다**

## Git
- 브랜치: `{type}/BE/{이슈번호}-{설명}` (예: `fix/BE/45-server-timezone`)
- 커밋과 PR 제목: `[BE] {type}: 설명 (#이슈)`. type은 feat / fix / refactor / chore / test / docs
- PR 대상은 `dev`이고 `main`에는 직접 push하지 않는다. PR은 `.github/pull_request_template.md` 양식을 따른다

## 금지
- `.env`, 키, 토큰을 커밋하거나 출력하지 않는다
- 테스트를 지우거나 건너뛰어서 통과시키지 않는다

## 개인 지시문
- 개인 지시문은 `AGENTS.override.md`에 둔다 (커밋되지 않음). 이 파일은 AGENTS.md를 **대체**하므로 공용 내용도 함께 담아야 한다
