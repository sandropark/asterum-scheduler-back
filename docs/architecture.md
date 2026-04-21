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
        - **중복 전략**: soft-conflict (등록 허용, `status=CONFLICT` 표기). 상세는 @docs/설계.md 참조

    - 일정 수정
        - 반복 일정 수정: scope: THIS_ONLY | THIS_AND_FUTURE | ALL
        - 유형 전환: 단일 일정 -> 반복 일정으로 전환 가능
    - 일정 삭제
        - 반복 일정 삭제: scope: THIS_ONLY | THIS_AND_FUTURE | ALL

- 장소
    - 시간대에 가능한 장소 목록 조회

- ## 인프라
    - psql
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

### 백엔드 설계 상세

> 테이블 스키마, 유즈케이스 플로우, 수정/삭제 scope 처리, 중복 전략(soft-conflict + partial exclusion), 단일→반복 전환, 작업 순서 등 구현 레벨 상세는 `docs/설계.md` 참조.

