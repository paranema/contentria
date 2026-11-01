# notification 컨텍스트 (알림)

다른 바운디드 컨텍스트에서 발생한 사건(구독 중인 블로그의 새 글 발행, 내 글에 달린 댓글, 내 댓글에 달린 답글)을 회원에게 알리는 일반(Generic) 서브도메인입니다.

| 애그리거트     | 내부 구성 요소 | 공개 범위               |
| -------------- | -------------- | ----------------------- |
| `Notification` | 없음           | 알림 수신자 본인만 조회 |

- **내부 아키텍처: 레이어드.** 도메인 이벤트를 구독하여 알림 레코드를 저장하고 사용자의 알림함 조회·읽음 처리를 제공하는 단순한 흐름이므로 4계층 레이어드 아키텍처를 적용합니다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md) 참고).
- **모듈 위치**: `backend/contexts/notification/` (`notification-api`, `notification-core`)
- **생명주기**: 생성된 지 90일이 지난 알림은 읽음 여부와 무관하게 매일 자동으로 삭제됩니다.

## 문서 목록

| 문서                                         | 다루는 내용                                                          |
| -------------------------------------------- | -------------------------------------------------------------------- |
| [01-rules.md](01-rules.md)                   | 알림 발생 조건, 수신자 선정 규칙, 알림함 조회·읽음 처리 및 보존 기간 |
| [02-implementation.md](02-implementation.md) | 각 도메인 규칙의 계층별 배치, 이벤트 컨슈머·인박스 및 배치 삽입 구현 |

## 관련 문서

| 문서                                                                               | 다루는 내용                                                             |
| ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| [strategic/04-context-map.md](../../strategic/04-context-map.md)                   | `PostPublished`, `CommentAdded` 이벤트 구독 및 상호작용 구독자 목록 조회|
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 인박스(`inbox_event`) 기반의 멱등 이벤트 소비 파이프라인                |
| [contexts/interaction/01-rules.md](../interaction/01-rules.md)                     | 알림의 발생 원인이 되는 블로그 구독 및 댓글·답글 규칙                   |
