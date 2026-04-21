# Front

- ## 달력
    - ## 월간 뷰
        - 연월을 선택하면 해당 월의 일정을 조회한다.
        - 특정 날짜를 선택하면 "일정 생성 화면" 노출
        - 특정 일정을 선택하면 "일정 상세 화면" 노출
    - ## 일정 생성 화면 (Form)
        - Form
            - 제목
            - 시작시간 (연월일 시분)
            - 종료시간 (연월일 시분)
            - 장소 (리스트)
            - 참여자 (리스트)
            - 노트
            - 반복 옵션
                - 숫자 (interval: 매 N일/N주/N월)
                - 일, 주, 월 (enum)
                - 반복 타겟: null or 요일 or 날짜
            - 종료 조건
                - null or 특정 날짜 or 횟수
    - ## 일정 상세 화면
        - 상세 정보 노출
        - 수정 버튼 선택 시 "수정화면" 노출
        - 삭제 버튼 선택 시 반복일정의 경우 scope 선택 창 표시. 단일 일정은 바로 삭제.
    - ## 수정화면
        - "일정 생성 화면"과 동일. 기존값 노출
        - 반복일정의 경우 수정 후 submit 시 scope 선택 창 표시

# Back

## 도메인

- 일정
    - 단일 일정
    - 반복 일정
- 사용자
  > 최소한으로 구현
    - 이름, email, 그룹
    - mock 데이터 사용
- 장소
  > 최소한으로 구현
    - mock 데이터 사용

## API

- 사용자
    - 사용자 목록 조회
    - 팀 목록 조회

- 일정
    - 월 별 일정 조회 (list)
    - 일정 상세 조회
    - 일정 생성
        - **반복 가능**
            - 반복 옵션: `매일, 매주, 매월`
                - 매일: 따로 설정 X
                - 매주: 요일을 설정
                - 매월: 날짜를 설정
            - 종료 조건: `특정 날짜까지 반복 / 특정 횟수 반복 / 무기한 반복`
        - **참여자**
            - N명의 참여자 추가 가능.
            - 개인 또는 그룹 지정가능.
        - **중복 전략 (Soft-Conflict)**
            - 등록은 허용하되 겹치면 `EventInstances.status = CONFLICT` 로 표기 (Google Calendar 스타일)
            - 판별식: `StartTime < NewEndTime AND EndTime > NewStartTime` (실제 구현은 `tsrange(start, end, '[)') &&`)
            - 상세는 @docs/event_설계.md 참조

    - 일정 수정
        - 반복 일정 수정: scope: THIS_ONLY | THIS_AND_FUTURE | ALL
        - 유형 전환: 단일 일정 -> 반복 일정으로 전환 가능
    - 일정 삭제
        - 반복 일정 삭제: scope: THIS_ONLY | THIS_AND_FUTURE | ALL

- 장소
    - 시간대에 가능한 장소 목록 조회

- ## 인프라
    - psql
    - Redis (Redisson — 분산 락 · 멱등성)
    - Flyway

- 모듈
    - 도메인 별로 분리 (Gradle 멀티 모듈)
    - 의존 관계: `app` → `event`, `user`, `location` → `common`
  ```
  asterum-scheduler/
  ├── app/               ← Spring Boot 엔트리포인트 (bootJar)
  ├── common/            ← 공통 예외, 응답 포맷 등
  ├── event/
  │   ├── domain/        ← Entity, VO
  │   ├── application/   ← Service
  │   ├── infra/         ← Repository 구현체
  │   └── presentation/  ← Controller, DTO
  ├── user/
  │   ├── domain/
  │   ├── application/
  │   ├── infra/
  │   └── presentation/
  └── location/
      ├── domain/
      ├── application/
      ├── infra/
      └── presentation/
  ```

### 반복 일정 설계 요약

> 상세 설계는 `docs/event_설계.md`, 구현 현황은 `docs/event_진행상황.md` 참조. 본 문서는 architecture-level 핵심만 다룬다.

#### 데이터 모델 (4-table 하이브리드)

- `Events` — 일정 본체 + RRule(RFC 5545) 규칙
- `EventOverrides` — 반복 중 특정 날짜의 수정/삭제 기록 (`event_id`, `override_date`, `is_deleted`, 변경 필드)
- `EventInstances` — **펼쳐진 인스턴스**. 조회·중복 체크의 유일한 기준
  - `event_id` (원본 추적), `override_id` (예외 회차에만 채워짐)
  - `status ∈ {CONFIRMED, CONFLICT}`
    - 장소 없는 일정: 자동 `CONFIRMED`
    - 장소 있는 일정: 같은 장소 + 시간 겹침이면 `CONFLICT`, 아니면 `CONFIRMED`
- `SyncStatus(date_key, is_generated)` — 날짜별 전개 완료 플래그

RRule 계산은 `rrule4j` 라이브러리 활용.

#### 인스턴스 생성: Scheduler + On-demand

- **배치(자정)**: 향후 N년치 `EventInstances` 선제 생성 → `is_generated=TRUE`
- **온디맨드**: 조회·생성 시 `is_generated=FALSE` 면 즉시 전개 (배치 누락·미래 조회 대응)
- **선제적 다지기**: 일정 저장 트랜잭션 시작 전 해당 날짜의 `SyncStatus` 를 확인. 미생성이면 그 날짜의 반복 일정을 먼저 전개한 뒤 자기 일정 insert (반복 일정의 장소 선점 보장)

#### 중복 전략: Soft-Conflict

- Google Calendar 스타일. 등록은 허용하되 겹치면 `status=CONFLICT` 로 저장.
- 같은 장소 + 시간 겹침 판별식: `tsrange(start_time, end_time, '[)') && tsrange(:start, :end, '[)')` (PostgreSQL `tsrange` overlap, GiST 인덱스로 가속). 의미상 `StartTime < NewEndTime AND EndTime > NewStartTime` 와 동등.
- 충돌 검사 시 같은 회차(자기 자신)는 instance id 기준으로 제외해야 함. `event_id` 기준 제외는 같은 반복 시리즈의 다른 회차 충돌을 놓침.
- **PostgreSQL Exclusion Constraint 는 사용하지 않는다** (이전 설계에서 폐기 — soft-conflict 와 양립 불가).

#### 타임존

- KST(Asia/Seoul) 단일 타임존만 지원. DB 에는 KST 기준 `TIMESTAMP` 그대로 저장. 변환 없음.

#### 동시성 & 멱등성

- **분산 락**: Redisson, `location_id + date_key` 단위
  `락 획득 → tx begin → r/w → tx commit → 락 해제`
- **다일 예약**: `multiLock` 으로 여러 날짜 락을 한 번에 획득. 데드락 방지를 위해 항상 날짜 오름차순으로 락
- **장소 없는 일정**: 분산락 불필요. 트랜잭션만으로 처리
- **따닥 방지**: idempotency-key 헤더 또는 Redis `UserId+Action` 1~2s TTL

#### 수정 범위 (scope)

| scope | 처리 요약 |
|---|---|
| `THIS_ONLY` | `EventOverrides` insert + 해당 `EventInstances` 의 `override_id` · 시간 · 장소 · 상태 동기화 |
| `THIS_AND_FUTURE` | 기존 `Events.rrule` `UNTIL` 을 선택일 전날로 단축 + 새 `Events` 생성. 선택일 이후 기존 `EventOverrides` soft-delete 후 새 `Events` 기준 재생성, `EventInstances` 삭제 후 재전개 |
| `ALL` (제목/내용/사용자) | `Events` update + 해당 `Events` 의 모든 `EventOverrides` 의 동일 필드 일괄 수정 (시간 필드는 보존) |
| `ALL` (시간/rrule/장소) | `Events` update + 해당 `Events` 의 모든 `EventOverrides` · `EventInstances` (과거 포함) soft-delete 후 재생성 |

#### 삭제 (soft-delete)

- 모든 삭제는 `deleted_at` 컬럼 설정 (1 년 경과 시 별도 archive 테이블 이전 또는 물리 삭제)
- 단일 일정: `Events` · `EventInstances` 의 `deleted_at` 설정
- `THIS_ONLY` 삭제: `EventOverrides` (`is_deleted=true`) insert + 해당 `EventInstances` 에 `override_id` 연결 후 `deleted_at` 설정 (반복 시리즈의 첫 회차를 지워도 시리즈 유지)
- `THIS_AND_FUTURE` 삭제: `Events.rrule` 단축 + 선택일 이후 `EventOverrides` · `EventInstances` 의 `deleted_at` 설정
- `ALL` 삭제: `Events` · `EventInstances` · `EventOverrides` 모두 `deleted_at` 설정

#### 단일 → 반복 유형 전환

- 기존 단일 `Event` 에 rrule 추가 + 기존 `EventInstances` 삭제 후 반복 일정 기준으로 재전개

#### 충돌 자동 승격

- `CONFIRMED` 가 변경/삭제로 자리를 비우면, 같은 장소 · 시간의 `CONFLICT` 인스턴스 중 `Event.created_at` 오름차순으로 가장 빠른 1 건을 `CONFIRMED` 로 승격
- 수정/삭제 트랜잭션 내에서 처리

### 연관관계

- 사용자 1 : 일정 N
- 장소 1 : 일정 N (soft-conflict 로 동시 등록은 허용, UI 경고)
- `Events` 1 : `EventOverrides` N
- `Events` 1 : `EventInstances` N










