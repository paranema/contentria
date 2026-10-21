# notification 컨텍스트 (알림)

다른 컨텍스트에서 일어난 일을 회원에게 알리는 기록을 담당한다. 구독한 블로그의 새 글, 내 글의 댓글, 내 댓글의 답글이다. 일반 서브도메인이다.

| 애그리거트 | 멤버 | 공개 범위 |
|---|---|---|
| `Notification` | 없음 | 수신자 본인만 |

**내부 구조: 레이어드.** 이벤트를 받아 저장하고 보여주는 흐름이다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)).
코드 위치는 `backend/contexts/notification/`(`notification-api`, `notification-core`)이다. 지금은 모듈 골격, 아키텍처 규칙, 도메인, 스키마가 있다(N2).

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 누구에게 언제 알리는지, 알림함 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지, 이벤트, 단계 | 구현하는 사람 |

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/04-context-map.md](../../strategic/04-context-map.md) | `PostPublished`, `CommentAdded` 소비. 상호작용에 구독자를 묻는 것 |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 소비자와 인박스. 알림은 이벤트를 소비만 한다 |
| [contexts/interaction/01-rules.md](../interaction/01-rules.md) | 구독과 댓글의 규칙. 알림의 계기가 된 사건 |

## 레거시

레거시(`legacy-final`)에는 동작하는 알림 코드가 없다. `backend/blog-api/.../api/notification/`에 주석 처리한 엔티티 초안과 빈 서비스, 리포지토리만 있다.
초안의 알림 종류(`NEW_POST`, `NEW_COMMENT`, `NEW_REPLY`, `NEW_SUBSCRIBER`) 중 구독 알림은 이번에 만들지 않는다(01-rules의 "보류").
초안은 알림 문구(`message`)를 저장했지만, 여기서는 ID만 저장하고 문구는 화면이 만든다.

프론트엔드에는 알림 드롭다운(`frontend/src/components/dashboard/header/NotificationDropdown.tsx`)이 있고, 백엔드와 연결되어 있지 않다. 완성된 문구(`text`)를 받는 형태라 연결할 때 바뀐다.
