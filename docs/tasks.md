# 구현 태스크 목록

`architecture.md` 기반으로 추출한 단계별 구현 태스크.

> **규칙**: API 구현 시 Swagger(OpenAPI) 문서도 함께 작성한다.

---

## Phase 1 — 인프라 & 환경 설정

- [x] `docker-compose.yml` 작성 (PostgreSQL + btree_gist 확장 활성화)
- [x] `app/src/main/resources/application.yml` datasource / JPA 설정

---

## Phase 2 — Common 모듈

- [x] 공통 응답 포맷 `ApiResponse<T>` 작성
- [x] 공통 예외 클래스 정의 (`BusinessException`, 에러 코드 enum 등)
- [x] `@RestControllerAdvice` 전역 예외 핸들러 작성

---

## Phase 3 — User 모듈 (Mock)

- [x] `User` 엔티티 (id, 이름, email, 그룹)
- [x] `Team` 엔티티 (id, 이름)
- [x] `users`, `teams` 테이블 생성
- [x] Mock 데이터 삽입
- [x] `GET /users` — 사용자 목록 조회 API
- [x] `GET /teams` — 팀 목록 조회 API

---

## Phase 4 — Location 모듈 (Mock)

- [x] `Location` 엔티티 (id, 이름, 수용 인원 등 최소 필드)
- [x] `locations` 테이블 생성
- [x] Mock 데이터 삽입
- [x] `GET /locations?start={datetime}&end={datetime}` — 해당 시간대에 예약 가능한 장소 목록 조회 API

---

## Phase 5 — Event 모듈 기반 (DB 스키마 & 도메인)

- [x] `events` 테이블 (단일 일정)
  ```sql
  events (id, title, start_time, end_time, location_id, notes, creator_id)
  ```
- [ ] `recurring_events` 테이블 (반복 규칙 템플릿)
  ```sql
  recurring_events (id, title, rrule, start_time, end_time, location_id, notes)
  -- rrule: RFC 5545 형식 (e.g. "FREQ=WEEKLY;BYDAY=MO,WE")
  ```
- [ ] `event_exceptions` 테이블 (개별 예외 레코드)
  ```sql
  event_exceptions (id, recurring_event_id, original_date, status, title, start_time, end_time)
  -- status: MODIFIED | DELETED
  ```
- [x] PostgreSQL Exclusion Constraint 적용 (동시성 제어)
  ```sql
  EXCLUDE USING gist (location_id WITH =, tsrange(start_time, end_time) WITH &&)
  ```
- [ ] `event` 모듈 `build.gradle.kts`에 `rrule4j` 의존성 추가
- [x] 도메인 엔티티: `Event` (단일 일정)
- [ ] 도메인 엔티티: `RecurringEvent` (반복 규칙 템플릿)
- [ ] 도메인 엔티티: `EventException` (예외 레코드)
- [ ] VO: `RecurrenceScope` enum (`THIS_ONLY`, `THIS_AND_FUTURE`, `ALL`)
- [ ] VO: `ExceptionStatus` enum (`MODIFIED`, `DELETED`)
- [ ] 참여자 연관관계 매핑: `event_participants` 테이블 (event_id, user_id or team_id)

---

## Phase 6 — Event API: 조회

- [ ] `GET /events?year={year}&month={month}` — 월별 일정 조회
    - `recurring_events`에서 해당 월 범위로 RRule 전개 → 가상 날짜 목록 생성
    - `event_exceptions`에서 예외 조회 후 병합 (MODIFIED → 대체, DELETED → 제외)
    - 단일 `events`도 포함하여 통합 응답 반환
- [ ] `GET /events/{id}` — 일정 상세 조회 (단일/반복 구분)

---

## Phase 7 — Event API: 단일 일정 CRUD

- [x] `POST /events` — 단일 일정 생성
    - 필드: title, start_time, end_time, location_id, participants, notes
    - [x] Exclusion Constraint 위반 시 에러 응답 처리
- [x] `PUT /events/{id}` — 단일 일정 수정
- [x] `DELETE /events/{id}` — 단일 일정 삭제

---

## Phase 8 — Event API: 반복 일정

- [ ] `POST /events` — 반복 일정 생성 (`recurrence` 필드 포함 시 반복 처리)
    - 반복 옵션
        - 매일: `FREQ=DAILY;INTERVAL=N`
        - 매주: `FREQ=WEEKLY;INTERVAL=N;BYDAY=MO,WE,...`
        - 매월: `FREQ=MONTHLY;INTERVAL=N;BYMONTHDAY=15`
    - 종료 조건: `UNTIL={date}` / `COUNT={n}` / 없음(무기한)
    - 참여자: 개인(user_id) 또는 그룹(team_id) N명 지정 가능
- [ ] `PUT /events/{id}?scope={scope}` — 반복 일정 수정
    - `THIS_ONLY`: `event_exceptions`에 `MODIFIED` 레코드 insert
    - `THIS_AND_FUTURE`: 기존 `recurring_events` 종료일 단축 + 새 `recurring_events` 생성
    - `ALL`: `recurring_events` update + 연관 `event_exceptions` 전체 delete
- [ ] `DELETE /events/{id}?scope={scope}` — 반복 일정 삭제
    - `THIS_ONLY`: `event_exceptions`에 `DELETED` 레코드 insert
    - `THIS_AND_FUTURE`: 기존 `recurring_events` 종료일을 삭제 대상 전날로 단축
    - `ALL`: `recurring_events` + 연관 `event_exceptions` 전부 delete
- [ ] 단일 → 반복 유형 전환
    - 기존 `events` 레코드를 `recurring_events`로 이전
    - 원래 일정을 첫 번째 발생일로 설정

---

## Phase 9 — Frontend (추후)

- [ ] 월간 달력 뷰 (연월 선택 → 해당 월 일정 조회)
- [ ] 일정 생성 Form (title, start/end time, location, participants, notes, 반복 옵션, 종료 조건)
- [ ] 일정 상세 화면 (상세 정보 노출, 수정/삭제 버튼)
- [ ] 수정 화면 (생성 Form과 동일, 기존값 pre-fill)
- [ ] Scope 선택 모달 (반복 일정 수정/삭제 시 THIS_ONLY / THIS_AND_FUTURE / ALL 선택)
