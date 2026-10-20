# 컨텍스트별 문서

컨텍스트 목록과 경계는 [strategic/03-bounded-contexts.md](../strategic/03-bounded-contexts.md)에 있다.
이 폴더에는 컨텍스트 하나에만 해당하는 규칙을 둔다.

## 목록

| 컨텍스트 | 폴더 | 내부 구조 | 문서 |
|---|---|---|---|
| 회원 | [member/](member/README.md) | 헥사고날 | 01-rules, 02-implementation |
| 출판 | [publishing/](publishing/README.md) | 헥사고날 | 01-rules, 02-implementation |
| 상호작용 | [interaction/](interaction/README.md) | 레이어드 | 01-rules, 02-implementation |
| 미디어 | media/ | 헥사고날 | 아직 없음 |
| 분석 | analytics/ | 레이어드 | 아직 없음 |
| 알림 | notification/ | 레이어드 | 아직 없음 |

내부 구조를 고른 이유는 [architecture/02-internal-architecture.md](../architecture/02-internal-architecture.md)에 있다.

## 폴더 구성

| 파일 | 내용 | 바뀌는 때 |
|---|---|---|
| `README.md` | 담당 범위, 애그리거트, 내부 구조, 다른 문서와의 관계 | 범위나 내부 구조가 바뀔 때 |
| `01-rules.md` | 제품이 어떻게 동작하는지. 아키텍처를 모른다 | 제품 규칙이 바뀔 때 |
| `02-implementation.md` | 01의 각 규칙을 어느 계층에 두는지 | 구현 방법이 바뀔 때 |

폴더는 문서를 쓸 때 만든다. 빈 폴더를 미리 두지 않는다.
