# 아키텍처 문서

이 디렉터리는 백엔드 아키텍처 결정과 규칙을 기록한다.

## 문서 목록

| 파일 | 내용 |
|---|---|
| [01-module-structure.md](01-module-structure.md) | Gradle 모듈 구성, 모듈을 나누는 기준 |
| [02-hexagonal-architecture.md](02-hexagonal-architecture.md) | 레이어 정의, 패키지 구조, 의존 방향 |
| [03-bounded-contexts.md](../strategic/03-bounded-contexts.md) | 바운디드 컨텍스트 목록, 컨텍스트 간 통신 규칙 |
| [04-persistence-conventions.md](04-persistence-conventions.md) | 엔티티 작성 규칙, 식별자 전략, 공통 필드 |

## 요약

- 도메인 로직은 전부 `core` 모듈에 둔다.
- `app/` 하위 모듈은 배포 단위이며, 인바운드 어댑터 역할만 한다.
- 최상위 패키지는 레이어가 아니라 바운디드 컨텍스트다.
- 의존 방향은 항상 안쪽(adapter → application → domain)이다.
- 컨텍스트 간 참조는 상대의 인바운드 포트를 통해서만 한다.

## 규칙 강제

문서에 적힌 규칙 중 기계적으로 검증 가능한 것은 ArchUnit 테스트로 강제한다.
테스트 위치는 `core/src/test/kotlin/com/contentria/ArchitectureTest.kt`이며,
CI에서 실패하면 머지할 수 없다.

규칙을 바꿔야 할 상황이 생기면 테스트를 우회하지 말고 문서와 테스트를 함께 수정한다.