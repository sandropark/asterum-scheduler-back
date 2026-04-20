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
        - **동시성 제어**
            - 한 장소에는 복수의 일정이 잡히면 안된다.
            - PostgreSQL Exclusion Constraint로 시간 범위 중복을 DB 레벨에서 원자적으로 차단
              ```sql
              EXCLUDE USING gist (location_id WITH =, tsrange(start_time, end_time) WITH &&)
              ```

    - 일정 수정
        - 반복 일정 수정: scope: THIS_ONLY | THIS_AND_FUTURE | ALL
        - 유형 전환: 단일 일정 -> 반복 일정으로 전환 가능
    - 일정 삭제
        - 반복 일정 삭제: scope: THIS_ONLY | THIS_AND_FUTURE | ALL

- 장소
    - 시간대에 가능한 장소 목록 조회

- ## 인프라
    - psql
    - Flyway는 추후 도입

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

### 반복 일정 DB 저장 전략: 하이브리드 방식

**템플릿(RRule) + 예외(Exception) 레코드** 구조를 사용한다.

#### 테이블 구조

```
recurring_events (반복 규칙 템플릿)
├── id
├── title
├── rrule          ← RFC 5545 형식 문자열 (e.g. "FREQ=WEEKLY;BYDAY=MO,WE")
├── start_time
├── end_time
├── location_id
└── notes

event_exceptions (개별 예외 레코드)
├── id
├── recurring_event_id → recurring_events.id
├── original_date      ← 원래 이 날짜였던 것
├── status             ← MODIFIED | DELETED
├── title              ← override (수정된 경우)
├── start_time         ← override
└── end_time           ← override
```

#### 조회 흐름 (월별 달력)

1. `recurring_events`에서 해당 월 범위로 RRule 전개 → 가상 날짜 목록 생성
2. `event_exceptions`에서 해당 `recurring_event_id`의 예외 조회
3. 병합: `MODIFIED`는 예외 내용으로 대체, `DELETED`는 제외

- RRule 계산은 `rrule4j` 라이브러리 활용

#### 수정/삭제 범위별 처리

**수정**

| 범위       | 처리 방식                                                               |
|----------|---------------------------------------------------------------------|
| 이 일정만    | `event_exceptions`에 `MODIFIED` 레코드 insert                           |
| 이후 모든 일정 | 기존 `recurring_events` 종료일을 전날로 단축 + 변경 내용으로 새 `recurring_events` 생성 |
| 전체 일정    | `recurring_events` update + 연관 `event_exceptions` 전체 delete         |

**삭제**

| 범위       | 처리 방식                                                |
|----------|------------------------------------------------------|
| 이 일정만    | `event_exceptions`에 `DELETED` 레코드 insert             |
| 이후 모든 일정 | 기존 `recurring_events` 종료일을 삭제 대상 전날로 단축              |
| 전체 일정    | `recurring_events` + 연관 `event_exceptions` 전부 delete |

#### 단일 → 반복 유형 전환

- 기존 단일 이벤트를 `recurring_events`로 이전, 해당 일정을 첫 번째 발생일로 설정

### 연관관계

- 사용자 1 : 일정 N
- 장소 1 : 일정 N (중첩 X)
- 반복규칙(recurring_events) 1 : 예외(event_exceptions) N










