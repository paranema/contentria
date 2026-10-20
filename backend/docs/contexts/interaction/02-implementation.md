# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 레이어드 규칙을 따른다(`presentation`, `application`, `domain`, `infra`).
불변식과 정책을 가르는 기준은 회원, 출판과 같다. 애그리거트 하나만 보고 판단할 수 있으면 도메인, 다른 애그리거트나 다른 컨텍스트를 조회해야 하면 앱이다.

## 애그리거트 경계

| 애그리거트 | 담는 것 | 다른 애그리거트 참조 |
|---|---|---|
| `Subscription` | 구독자 ID, 블로그 ID, 구독 시각 | 모두 ID |
| `Like` | 회원 ID, 글 ID, 블로그 ID, 시각 | 모두 ID |
| `Comment` | 글 ID, 블로그 ID, 작성자 ID, 부모 댓글 ID, 본문, 수정 여부, 지운 자리 여부 | 부모 댓글도 ID |

**댓글 하나하나가 애그리거트다.** 답글은 부모와 따로 쓰이고 따로 고쳐진다. 부모와 답글을 한 애그리거트로 묶으면, 인기 댓글에 답글이 몰릴 때 모두가 같은 애그리거트를 두고 경합한다.
그래서 "답글이 있으면 자리를 남긴다"는 답글이 있는지 조회해야 판단할 수 있고, 앱의 규칙이 된다. 자리를 남기는 동작 자체는 도메인 메서드(`Comment.leavePlaceholder()`)다.

트랜잭션 하나는 애그리거트 하나를 바꾼다. 예외가 하나 있다. 지운 자리만 남은 댓글의 마지막 답글을 지우면, 같은 트랜잭션에서 그 자리도 지운다(01 흐름 4). 남길 이유가 사라진 것을 정리하는 일이고, 따로 나누면 빈 자리가 남는 순간이 생긴다.

블로그 ID를 좋아요와 댓글에 함께 둔다. `CommentAdded`가 블로그를 담고, 블로그별 집계가 필요해질 때 다시 출판에 묻지 않게 한다. 쓸 때 출판에서 이미 읽은 값이다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `subscriptions(subscriber_id, blog_id)` UNIQUE | 한 회원이 한 블로그를 한 번만 구독 |
| `likes(account_id, post_id)` UNIQUE | 한 회원이 한 글에 한 번만 좋아요 |
| `comments(post_id, created_at)` 인덱스 | 글의 댓글을 시간순으로 |
| `comments(parent_id)` 인덱스 | 답글이 있는지, 답글 목록 |
| `comments` CHECK `removed = (body = '')` | 지운 자리만 본문이 비어 있다 |
| `comments` CHECK `NOT removed OR parent_id IS NULL` | 답글은 지운 자리가 되지 않는다. 실제로 지운다 |
| `outbox_event`, `inbox_event` | 아웃박스는 출판과 같은 형태. 인박스는 `(event_id, consumer)` 기본키 |

외래키는 없다. 세 애그리거트 모두 다른 애그리거트와 다른 컨텍스트를 ID로만 참조한다. 답글의 부모도 다른 애그리거트다.

같은 구독이나 좋아요가 동시에 두 번 오면 고유 제약이 둘째를 막는다. 01의 "이미 구독함은 성공"에 따라 그 실패도 성공으로 답한다.

## 동시성

댓글은 외래키가 없으므로, 부모와 답글 사이의 규칙은 잠금으로 지킨다. 최상위 댓글 아래의 변경은 모두 그 댓글 행을 `SELECT ... FOR UPDATE`(`Comment.findForUpdate`)로 먼저 잠근다.

| 변경 | 잠그는 행 | 막는 것 |
|---|---|---|
| 답글 쓰기 | 부모 | 지우는 중인 댓글에 답글이 붙는 것 |
| 최상위 댓글 지우기 | 그 댓글 | 답글이 없다고 보고 지우는 사이에 답글이 붙는 것 |
| 답글 지우기 | 그 답글, 그다음 부모 | 마지막 두 답글이 동시에 지워져 서로 상대가 남았다고 보고 빈 자리를 남기는 것 |
| 고치기 | 그 댓글 | 자리를 남기는 동안 고쳐 본문이 되살아나는 것 |

답글은 자기 행을 먼저, 부모를 나중에 잠근다. 부모를 쥔 채 답글을 기다리는 쪽은 없으므로 교착이 생기지 않는다.

## 다른 컨텍스트 호출

| 호출 | 위치 | 내용 |
|---|---|---|
| 회원: 계정이 ACTIVE인가 | `infra/client/MemberClient` | `member-api`의 `GetAccountStatusQuery` |
| 출판: 글이 발행 상태인가, 그 블로그와 소유자 | `infra/client/PublishingClient` | `publishing-api`에 새로 공개할 조회. 글 ID 묶음으로 묻는다 |
| 출판: 블로그 slug로 블로그 ID와 소유자 | `infra/client/PublishingClient` | 위와 같다 |

쓰기 트랜잭션을 열기 **전에** 조회한다. 출판이 응답하지 않으면(분리 후) 쓰기를 거부한다. 거두는 일은 출판과 회원에게 묻지 않는다.

출판은 `publishing-api`에 `GetPostReferencesQuery.getPosts(글 ID 묶음)`(블로그, 소유자, 공개 여부)와 `GetBlogReferencesQuery`(`getBlogBySlug`, `getBlogs`)를 공개한다. 출판의 읽기 전용 트랜잭션에서 돌고 본문은 읽지 않는다.
번역 계층은 출판과 회원의 타입을 상호작용의 타입(`domain/target`의 `TargetPost`, `TargetBlog`)으로 바꾼다. 발행되지 않은 글, 출판이 모르는 글, 회원이 모르는 계정은 모두 "할 수 없음"이 된다. 레이어드에서 infra는 domain만 알 수 있으므로 이 타입을 domain에 둔다.

## 이벤트

| 이벤트 | 방향 | 토픽 | 상호작용이 하는 일 |
|---|---|---|---|
| `PostDeleted` | 출판 → 상호작용 | `publishing.post` | 그 글의 댓글과 좋아요를 지운다. 소비자 그룹 `interaction.post-deleted` |
| `CommentAdded` | 상호작용 → 알림 | `interaction.comment` | 댓글을 쓴 트랜잭션에서 `interaction.outbox_event`에 기록한다 |

`CommentAdded`가 담는 것: 댓글 ID, 글 ID, 블로그 ID, 블로그 소유자 ID, 작성자 ID, 부모 댓글의 작성자 ID(답글일 때). 알림이 누구에게 보낼지 다시 묻지 않게 한다.

상호작용이 Kafka를 처음 쓰는 컨텍스트다. 이 단계에서 다음을 함께 만든다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "이벤트").

| 만들 것 | 위치 |
|---|---|
| 아웃박스 릴레이 | `platform/messaging`. 보내지 않은 행을 발생 순서대로 Kafka로 보내고 보낸 시각을 남긴다. 프로세스에 레플리카가 여럿이어도 하나만 돈다 |
| 인박스 | `platform/messaging`의 `Inbox.processOnce`와 `interaction.inbox_event`. 처리와 같은 트랜잭션에서 `(eventId, 소비자)`를 기록해 중복을 거른다 |
| 소비자 | `presentation/messaging/PostDeletedConsumer` |

## 공개 조회

| 조회 | 위치 |
|---|---|
| 좋아요 수, 댓글 수(글 ID 묶음), 구독자 수(블로그 ID 묶음) | `interaction-api`에 공개한다. 글 목록과 상세를 조합하는 진입점이 쓴다 |
| 내가 좋아요했는지, 내가 구독했는지 | HTTP |

`interaction-api`의 `GetInteractionCountsQuery`(`likeCounts`, `commentCounts`, `subscriberCounts`)는 묻는 ID마다 값을 돌려준다. 아무도 누르지 않은 ID는 0이다. 댓글 수는 답글을 넣고 지운 자리는 넣지 않는다.

## HTTP

| 요청 | 응답 | 01 |
|---|---|---|
| `PUT /blogs/{slug}/subscription` | 204. 이미 구독했어도 204 | 흐름 1 |
| `DELETE /blogs/{slug}/subscription` | 204. 구독하지 않았거나 없는 블로그여도 204 | 흐름 1 |
| `GET /me/subscriptions` | 구독한 블로그(ID, slug, 구독 시각), 최근 구독 순 | 공개 범위 |
| `GET /me/subscriptions/{slug}` | `{"subscribed": true}` | 공개 범위 |
| `PUT /posts/{id}/like` | 204. 이미 눌렀어도 204 | 흐름 2 |
| `DELETE /posts/{id}/like` | 204. 누르지 않았어도, 글이 숨겨졌어도 204 | 흐름 2 |
| `GET /me/likes?postIds=` | 그중 내가 좋아요한 글 ID | 공개 범위 |
| `GET /interactions/counts?postIds=&blogIds=` | 좋아요 수, 댓글 수, 구독자 수. 로그인하지 않아도 된다 | 공개 범위 |
| `GET /posts/{id}/comments?cursor=` | 최상위 댓글 20개씩 오래된 순, 각자의 답글 전부. 로그인하지 않아도 된다 | 공개 범위 |
| `POST /posts/{id}/comments` | 201과 쓴 댓글. `parentId`를 주면 답글 | 흐름 3 |
| `PATCH /comments/{id}` | 고친 댓글. `edited`가 true | 흐름 4 |
| `DELETE /comments/{id}` | 204. 답글이 있으면 자리만 남는다 | 흐름 4 |

지운 자리는 `removed`가 true이고, 본문은 비어 있으며 작성자 ID를 보여주지 않는다. 작성자 이름은 화면을 조합하는 쪽이 회원에게 묻는다. 회원은 아직 이름 조회를 공개하지 않았다.

ID 묶음은 한 번에 100개까지 받는다. 넘으면 400 `TOO_MANY_IDS`다.

| 실패 | 응답 |
|---|---|
| 계정이 ACTIVE가 아님 | 403 `ACCOUNT_NOT_ACTIVE` |
| 블로그가 없음 | 404 `BLOG_NOT_FOUND` |
| 글이 없거나 발행 상태가 아님 | 404 `POST_NOT_FOUND` |
| 자기 블로그, 자기 글 | 409 `OWN_BLOG`, `OWN_POST` |
| 댓글이 없음 | 404 `COMMENT_NOT_FOUND`. 이미 지운 댓글을 다시 지워도 같다 |
| 부모가 그 글의 댓글이 아님 | 400 `INVALID_PARENT` |
| 답글의 답글 | 400 `REPLY_TO_REPLY` |
| 본문이 비었거나 1000자를 넘음 | 400 `INVALID_COMMENT` |
| 작성자가 아님 | 403 `NOT_COMMENT_AUTHOR` |
| 지운 자리를 고치거나 지우거나 거기에 답함 | 409 `COMMENT_REMOVED` |

같은 구독이나 좋아요가 동시에 두 번 오면 하나는 유니크 제약에 걸린다. 유스케이스는 이를 "이미 있음"과 같게 보고 성공으로 답한다.

## 도메인 메서드

| 01의 규칙 | 메서드 | 비고 |
|---|---|---|
| 흐름 1의 5번 | `Subscription.of(subscriberId, blogId)` | 블로그 존재와 소유자 확인은 유스케이스 |
| 흐름 2의 5번 | `Like.of(accountId, postId, blogId)` | 위와 같다 |
| 흐름 3의 7번 (최상위 댓글) | `Comment.write(postId, blogId, commenterId, body)` | |
| 흐름 3의 4, 5번 (답글) | `parent.reply(commenterId, body)` | 답글의 답글과 지운 자리의 답글을 거부한다 |
| 흐름 4의 1, 2, 4번 | `Comment.edit(editorId, body)` | 작성자만, 지운 자리는 안 됨, "수정됨" 표시 |
| 흐름 4의 5번 | `Comment.leavePlaceholder(requesterId)` | 답글이 있는지는 유스케이스가 조회한다 |
| 흐름 4의 6번 | `Comment.requireChangeableBy(accountId)` 뒤 실제 삭제 | |

레이어드 규칙은 `ContextRules.forLayered`가 강제한다. 계층은 presentation, application, infra 순으로만 의존하고, domain은 셋 모두가 쓴다. domain은 JPA, Spring Data `Repository` 마커, 공유 커널만 의존한다.

## 단계

| 단계 | 내용 | 완료 조건 |
|---|---|---|
| I0 | 이 문서와 01-rules, README | 결정 항목 확정 |
| I1 | 모듈 골격(`interaction-api`, `interaction-core`), 레이어드 ArchUnit 규칙, 도메인 | 도메인 규칙마다 테스트 |
| I2 | `interaction` 스키마와 롤, 마이그레이션(구독, 좋아요, 댓글, 아웃박스, 인박스) | 빈 DB에 적용, 스키마를 넘는 FK 검사 통과 |
| I3 | `publishing-api`의 글·블로그 조회, 상호작용의 번역 계층(출판, 회원) | 발행되지 않은 글과 정지된 계정의 쓰기 거부 |
| I4 | 구독과 좋아요 유스케이스, 웹, 수 조회(`interaction-api`) | 01 흐름 1, 2의 HTTP 테스트 |
| I5 | 댓글 유스케이스와 웹, `CommentAdded` 아웃박스 | 01 흐름 3, 4의 HTTP 테스트 |
| I6 | Kafka, 아웃박스 릴레이, 인박스, `PostDeleted` 소비 | 글을 지우면 댓글과 좋아요가 정리되는 테스트(Kafka 컨테이너) |
