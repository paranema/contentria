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

블로그 ID를 좋아요와 댓글에 함께 둔다. `CommentAdded`가 블로그를 담고, 블로그별 집계가 필요해질 때 다시 출판에 묻지 않게 한다. 쓸 때 출판에서 이미 읽은 값이다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `subscriptions(subscriber_id, blog_id)` UNIQUE | 한 회원이 한 블로그를 한 번만 구독 |
| `likes(account_id, post_id)` UNIQUE | 한 회원이 한 글에 한 번만 좋아요 |
| `comments(post_id, created_at)` 인덱스 | 글의 댓글을 시간순으로 |
| `comments(parent_id)` 인덱스 | 답글이 있는지, 답글 목록 |

외래키는 없다. 세 애그리거트 모두 다른 애그리거트와 다른 컨텍스트를 ID로만 참조한다. 답글의 부모도 다른 애그리거트다.

같은 구독이나 좋아요가 동시에 두 번 오면 고유 제약이 둘째를 막는다. 01의 "이미 구독함은 성공"에 따라 그 실패도 성공으로 답한다.

## 다른 컨텍스트 호출

| 호출 | 위치 | 내용 |
|---|---|---|
| 회원: 계정이 ACTIVE인가 | `infra/client/MemberClient` | `member-api`의 `GetAccountStatusQuery` |
| 출판: 글이 발행 상태인가, 그 블로그와 소유자 | `infra/client/PublishingClient` | `publishing-api`에 새로 공개할 조회. 글 ID 묶음으로 묻는다 |
| 출판: 블로그 slug로 블로그 ID와 소유자 | `infra/client/PublishingClient` | 위와 같다 |

쓰기 트랜잭션을 열기 **전에** 조회한다. 출판이 응답하지 않으면(분리 후) 쓰기를 거부한다. 거두는 일은 출판과 회원에게 묻지 않는다.

출판은 아직 이 조회를 공개하지 않는다. `publishing-api`에 추가한다(I3).

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
