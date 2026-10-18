# 아키텍처 문서

이 디렉터리는 [strategic/](../strategic/README.md)에서 정한 경계를 코드로 옮기는 규칙을 기록한다.
컨텍스트 목록과 경계를 나눈 이유는 strategic에 있다. 이 디렉터리에서는 다시 적지 않는다.

## 문서 목록

번호는 읽는 순서다.

| 파일 | 내용 |
|---|---|
| [01-module-structure.md](01-module-structure.md) | 디렉터리와 Gradle 모듈 구성, 의존 규칙, 조립 방식, 서비스 분리 시 바뀌는 것 |
| [02-internal-architecture.md](02-internal-architecture.md) | 컨텍스트 내부 구조(헥사고날, 레이어드)를 고르는 기준과 각 구조의 규칙 |
| [03-context-integration.md](03-context-integration.md) | 컨텍스트 간 조회, 이벤트(Kafka, 아웃박스, 인박스), 트랜잭션 규칙 |
| [04-persistence-conventions.md](04-persistence-conventions.md) | 데이터베이스, 컨텍스트별 스키마, DB 롤, 마이그레이션(Flyway), 아웃박스·인박스 테이블. 엔티티 작성 규칙은 작성 예정 |

## 요약

- 최상위 축은 바운디드 컨텍스트다. 컨텍스트마다 `<컨텍스트>-api`와 `<컨텍스트>-core` 모듈을 둔다.
- 다른 컨텍스트에는 `-api`만 공개한다. `-core` 사이에는 의존성이 없다.
- 컨텍스트 내부 구조는 서브도메인 분류에 따라 고른다. 바깥 계약은 모든 컨텍스트가 같다.
- `apps/*`는 컨텍스트를 묶어 프로세스로 띄우는 조립 모듈이다. 요청 인증과 화면 조합만 직접 가진다.
- 컨텍스트 간 조회는 원격 호출처럼 다루고, 후속 처리는 아웃박스와 Kafka로 넘긴다.
- 한 트랜잭션은 한 컨텍스트의 쓰기만 담는다.
- 컨텍스트마다 DB 스키마를 따로 둔다. 다른 스키마를 SQL로 참조하지 않는다.

## 규칙 강제

문서에 적힌 규칙 중 기계적으로 검증 가능한 것은 Gradle 의존성과 ArchUnit 테스트로 강제한다.

규칙은 `platform/architecture-rules`에 있고, 각 컨텍스트(`MemberArchitectureTest`)와 app(`apps/api`의 `ArchitectureTest`)이 테스트에서 불러 쓴다. 구현과 자체 검증 방법은 [02-internal-architecture.md](02-internal-architecture.md)의 "ArchUnit"에 있다.

규칙을 바꿔야 할 상황이 생기면 테스트를 우회하지 말고 문서와 테스트를 함께 수정한다.
