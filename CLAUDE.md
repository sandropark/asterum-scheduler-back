# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 아키텍처 및 설계 결정

@README.md
@docs/architecture.md: 설계 결정

## 작업규칙

- 코드 탐색 시 serena mcp를 적극 사용
- 레퍼런스가 필요한 경우 context7 mcp를 적극 사용
- 복잡한 설계/코드 작업 시 sequential-thinking mcp를 적극 사용

## 빌드/실행/테스트 명령

기본 명령은 README.md 참조. Claude 가 잊지 말 가드레일:

- 로컬 기동은 항상 `--spring.profiles.active=dev` 필요. 기본 `application.yml` 에는 datasource 가 없어 누락 시 기동 실패.
- 단일 테스트: `./gradlew :event:test --tests "*EventServiceTest.일정을 생성하면*"` — 백틱 한글 메서드명도 패턴 매칭됨.
- 통합 테스트(`:app:test`)는 Testcontainers 가 Postgres 16 컨테이너를 자동 기동하므로 `docker-compose up -d` 불필요.

## 도메인 모듈 패키지 레이아웃

각 도메인 모듈(event/user/location)은 동일 레이아웃을 따른다:

```
domain/        ← @Entity, VO. JPA 어노테이션은 여기까지.
application/   ← @Service, Request/Response DTO, *ReaderImpl
infra/         ← Spring Data JpaRepository, DataInitializer
presentation/  ← Controller, OpenAPI 인터페이스(*Api.kt)
```

Spring Boot 진입점은 `app/` 만. 다른 모듈은 라이브러리 jar 로 빌드되며 `@SpringBootApplication` 이 `com.sandro.asterumscheduler` 루트를 스캔하므로 도메인 모듈 컴포넌트가 자동 픽업된다.

## 핵심 패턴: Cross-Module Reader 인터페이스

도메인 모듈끼리는 직접 의존하지 않는다. 다른 도메인 데이터가 필요하면 `common/<domain>/` 에 정의된 Reader 인터페이스에만 의존한다.

- 인터페이스 위치: `common/src/.../common/<domain>/<Domain>Reader.kt` (예: `UserReader`, `LocationReader`, `EventReader`)
- 구현 위치: 해당 도메인 모듈의 `application/<Domain>ReaderImpl.kt` — `@Component`로 빈 등록

새 도메인 간 호출이 필요할 때 절대 `event` → `user` 식의 모듈 의존을 추가하지 말고 Reader 인터페이스에 메서드를 추가하라.

## DB / 마이그레이션

- Flyway가 `app/src/main/resources/db/migration` 의 `V*__*.sql`을 자동 적용. JPA `ddl-auto`는 `validate`이므로 스키마 변경은 항상 마이그레이션 파일로.
- 장소 시간 중복 방지는 DB 레벨 Exclusion Constraint (`btree_gist`):
  - `tsrange(start_time, end_time, '[)')` + `location_id` 동등 비교
  - 위반 시 `DataIntegrityViolationException` → 서비스 계층에서 `BusinessException(ErrorCode.CONFLICT)`로 변환하는 것이 컨벤션 (`EventService.create/update` 참조)
- 테스트용 컨테이너는 별도 SQL을 실행하지 않고 Flyway가 V1, V2를 적용하므로 `btree_gist`가 자동 생성된다.

## 응답 / 예외 컨벤션

- 모든 컨트롤러 응답은 `ApiResponse<T>` 래퍼 (`common/response/ApiResponse.kt`).
- 비즈니스 에러는 `BusinessException(ErrorCode)` 만 던진다. `ErrorCode` 추가는 `common/exception/ErrorCode.kt`.
- 글로벌 처리는 `common/advice/GlobalExceptionHandler.kt`.

## 알려진 TODO / 가드레일

- `JpaConfig.auditorAware()` 가 항상 `0L` 을 반환한다. 인증이 들어오기 전까지 `createdBy/updatedBy` 는 의미 없는 값이다.
- 반복 일정(`recurring_events`, `event_exceptions`)은 아직 도메인/스키마 모두 미구현. `docs/architecture.md` 의 하이브리드 설계가 정답이다.
- `EventService.update` 의 단일→반복 전환 분기 미구현 (`// TODO` 표시됨).
