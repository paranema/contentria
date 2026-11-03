# 바운디드 컨텍스트별 설계 문서

전체 바운디드 컨텍스트 목록과 경계를 나눈 기준은 [strategic/03-bounded-contexts.md](../strategic/03-bounded-contexts.md)에 정의되어 있습니다.
이 디렉터리에는 각 컨텍스트 내부에만 해당하는 도메인 규칙과 구현 구조를 기록합니다.

## 컨텍스트 목록

| 컨텍스트 | 디렉터리                                | 내부 아키텍처 | 포함 문서                             |
| -------- | --------------------------------------- | ------------- | ------------------------------------- |
| 회원     | [member/](member/README.md)             | 헥사고날      | `01-rules.md`, `02-implementation.md` |
| 출판     | [publishing/](publishing/README.md)     | 헥사고날      | `01-rules.md`, `02-implementation.md` |
| 상호작용 | [interaction/](interaction/README.md)   | 레이어드      | `01-rules.md`, `02-implementation.md` |
| 미디어   | [media/](media/README.md)               | 헥사고날      | `01-rules.md`, `02-implementation.md` |
| 분석     | [analytics/](analytics/README.md)       | 레이어드      | `01-rules.md`, `02-implementation.md` |
| 알림     | [notification/](notification/README.md) | 레이어드      | `01-rules.md`, `02-implementation.md` |

컨텍스트마다 내부 아키텍처(헥사고날, 레이어드)를 다르게 선정한 기준은 [architecture/02-context-architecture.md](../architecture/02-context-architecture.md)에서 다룹니다.

## 컨텍스트 폴더 구성 규칙

| 파일명                 | 다루는 내용                                                          | 수정 시점                           |
| ---------------------- | -------------------------------------------------------------------- | ----------------------------------- |
| `README.md`            | 컨텍스트의 책임 범위, 주요 애그리거트, 내부 아키텍처, 관련 문서 안내 | 컨텍스트 범위나 아키텍처가 바뀔 때  |
| `01-rules.md`          | 아키텍처·기술과 무관한 비즈니스 및 제품 동작 규칙                    | 제품 정책이나 도메인 규칙이 바뀔 때 |
| `02-implementation.md` | `01-rules.md`의 규칙을 어느 계층과 컴포넌트에 배치해 구현하는지 정의 | 계층 배치나 기술 구현이 바뀔 때     |
