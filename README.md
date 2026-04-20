# asterum-scheduler-back

일정 관리 서비스 백엔드. 단일/반복 일정, 장소 중복 방지, 참여자 관리를 지원한다.

## 기술 스택

- Kotlin 2.2 / Spring Boot 4.0 / Java 21
- PostgreSQL (btree_gist 확장)
- Gradle 멀티 모듈

## 모듈 구조

```
app/        ← Spring Boot 엔트리포인트
common/     ← 공통 응답 포맷, 예외 처리
user/       ← 사용자·팀 API (mock)
location/   ← 장소 API (mock)
event/      ← 일정 도메인 (단일·반복)
```

의존 방향: `app` → `event`, `user`, `location` → `common`

## 로컬 실행

```bash
# DB 실행
docker-compose up -d

# 서버 실행 (Java 21 필요)
./gradlew :app:bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## 핵심 설계

- **반복 일정**: RRule(RFC 5545) 템플릿 + 예외 레코드 하이브리드 방식
- **동시성 제어**: PostgreSQL Exclusion Constraint로 장소 시간 중복 DB 레벨 차단
- **반복 수정/삭제 범위**: `THIS_ONLY` / `THIS_AND_FUTURE` / `ALL`
