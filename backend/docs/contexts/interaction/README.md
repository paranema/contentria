# interaction 컨텍스트 (상호작용)

로그인한 독자가 블로그와 글에 남기는 것을 담당한다. 블로그 구독, 댓글, 좋아요다. 지원 서브도메인이다.

| 애그리거트 | 멤버 | 공개 범위 |
|---|---|---|
| `Subscription` | 없음 | 수만 공개. 구독한 블로그 목록은 본인만 |
| `Like` | 없음 | 수만 공개 |
| `Comment` | 없음 | 공개 글의 댓글은 누구나 |

**내부 구조: 레이어드.** 규칙이 작고, 외부 연동이 출판과 회원 조회, 이벤트뿐이다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)).
코드 위치는 `backend/contexts/interaction/`(`interaction-api`, `interaction-core`)다. I0~I6을 마쳤다. 구독, 좋아요, 댓글의 유스케이스와 HTTP, 수 조회, `CommentAdded` 발행, `PostDeleted` 소비가 있다.

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 구독, 좋아요, 댓글 규칙 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지, 이벤트, 단계 | 구현하는 사람 |

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md) | 구독, 댓글, 좋아요를 한 컨텍스트로 묶은 이유. 좋아요 수를 상호작용이 소유하는 이유 |
| [strategic/04-context-map.md](../../strategic/04-context-map.md) | 출판과 회원에게 묻는 것, `PostDeleted` 소비, `CommentAdded` 발행 |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 릴레이, 소비자와 인박스. 상호작용이 Kafka를 처음 쓰는 컨텍스트다 |

## 레거시

레거시(`legacy-final`)에는 구독, 댓글, 좋아요 코드가 없다. 이 폴더의 규칙으로 처음 만든다.
