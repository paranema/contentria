# publishing 컨텍스트 (출판)

블로그, 카테고리, 게시글의 작성과 발행을 담당하는 핵심(Core) 서브도메인입니다.

| 애그리거트     | 내부 구성 요소                   | 공개 범위                                             |
| -------------- | -------------------------------- | ----------------------------------------------------- |
| `Blog`         | 없음                             | 전체 공개                                             |
| `CategoryTree` | `Category`                       | 전체 공개                                             |
| `Post`         | `PostSlug` (현재 슬러그 및 이력) | 발행된 게시글은 전체 공개, 나머지는 블로그 소유자만 조회 |

- **내부 아키텍처: 헥사고날.** 게시글 발행 상태 전이, 슬러그(slug) 이력 관리, 카테고리 트리 제약, 소유권 검증 등 도메인 규칙이 가장 풍부한 핵심 컨텍스트이므로 헥사고날 아키텍처를 적용합니다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md) 참고).
- **모듈 위치**: `backend/contexts/publishing/` (`publishing-api`, `publishing-core`)
- **이벤트 발행**: 아웃박스(`publishing.outbox_event`)에 기록된 도메인 이벤트는 `platform/messaging`의 릴레이가 Kafka `publishing.post` 토픽으로 전달합니다.

## 문서 목록

| 문서                                         | 다루는 내용                                                     |
| -------------------------------------------- | --------------------------------------------------------------- |
| [01-rules.md](01-rules.md)                   | 블로그, 카테고리, 게시글, 슬러그(slug) 관련 도메인 규칙         |
| [02-implementation.md](02-implementation.md) | 각 도메인 규칙의 계층별 배치, 애그리거트 경계 및 세부 구현 구조 |

- `01-rules.md`는 내부 아키텍처와 무관하게 제품이 어떻게 동작해야 하는지만 정의합니다.
- `02-implementation.md`는 `01-rules.md`의 규칙을 실제 코드 계층에 어떻게 배치했는지 기록하며, 내부 아키텍처나 기술 구현이 바뀌면 `02-implementation.md`만 수정합니다.

## 관련 문서

| 문서                                                                               | 다루는 내용                                                                          |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md)         | 블로그·카테고리·게시글을 한 컨텍스트로 묶은 배경, 조회수와 좋아요 수를 분리한 이유   |
| [strategic/04-context-map.md](../../strategic/04-context-map.md)                   | 회원·미디어·상호작용·알림 컨텍스트와의 연동 관계 및 `PostPublished`, `PostDeleted` 이벤트 |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 컨텍스트 간 동기 호출 원칙과 트랜잭셔널 아웃박스 패턴                                |
