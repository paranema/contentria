# 상호작용 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 블로그 구독, 게시글 좋아요, 댓글·답글 규칙을 실제 코드 계층과 데이터베이스에 어떻게 배치해 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

상호작용 컨텍스트(`contexts/interaction`)는 [architecture/02-context-architecture.md](../../architecture/02-context-architecture.md)의 **레이어드 아키텍처**(`presentation → application → domain ← infra`)를 따르며, 계층 간 의존 규칙은 ArchUnit의 `ContextRules.forLayered`가 검증합니다. 단일 애그리거트 상태만으로 판단할 수 있으면 도메인(`도메인`)에서 처리하고, 다른 애그리거트나 외부 컨텍스트를 조회해야 판단할 수 있으면 응용 계층(`앱`)에서 조율합니다.

### 스프링 설정 구성

스프링 설정은 역할에 따라 세 클래스로 분리되어 있으며, 현재는 모두 API 서버(`apps/api`)에서 로드됩니다.

| 설정 클래스                         | 등록 대상 컴포넌트                                                                         | 로드 프로세스 |
| ----------------------------------- | ------------------------------------------------------------------------------------------ | ------------- |
| `InteractionCoreConfiguration`      | 엔티티, 리포지토리, 응용 서비스, 외부 클라이언트(`infra/client`), 아웃박스(`OutboxSource`) | `apps/api`    |
| `InteractionWebConfiguration`       | 구독·좋아요·댓글·집계 REST 컨트롤러(`presentation/web`)                                    | `apps/api`    |
| `InteractionMessagingConfiguration` | `PostDeleted` 이벤트 컨슈머 및 멱등 인박스(`presentation/messaging`, `interactionInbox`)   | `apps/api`    |

## 데이터 모델

### 애그리거트 구성

상호작용 컨텍스트는 블로그 구독(`Subscription`), 게시글 좋아요(`Like`), 댓글(`Comment`) 세 개의 애그리거트로 구성됩니다. 세 애그리거트 모두 다른 애그리거트(부모 댓글 포함)와 타 컨텍스트의 자원을 ID 값으로만 참조하며 물리적 외래키(FK)를 두지 않습니다.

| 애그리거트 (`interaction` 스키마) | 보유 상태                                                                      | 영속화 및 설계 특징                                                                                                             |
| --------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `Subscription` (`subscriptions`)  | 구독자 ID, 블로그 ID, 구독 시각                                                | 회원과 블로그의 구독 관계를 나타내는 불변 엔티티입니다.                                                                         |
| `Like` (`likes`)                  | 회원 ID, 게시글 ID, 블로그 ID, 생성 시각                                       | 생성 시 출판 컨텍스트에서 확인한 `blog_id`를 함께 보관합니다.                                                                   |
| `Comment` (`comments`)            | 게시글 ID, 블로그 ID, 작성자 ID, 부모 댓글 ID, 본문, 수정 여부, 자리 표시 여부 | 부모 댓글과 하위 답글을 묶지 않고 **개별 댓글 하나하나를 독립 애그리거트**로 관리하여 답글 동시 작성 시 잠금 경합을 방지합니다. |

댓글과 좋아요에 `blog_id`를 함께 저장해 두는 이유는, 작성 시점에 이미 출판 컨텍스트에서 읽어 온 블로그 ID를 보관해 두면 `CommentAdded` 이벤트 페이로드를 구성하거나 블로그 단위 통계를 집계할 때 출판 컨텍스트를 다시 조회하지 않아도 되기 때문입니다.

### 데이터베이스 스키마

중복 방지와 데이터 무결성, 그리고 조회 성능을 보장하기 위해 `interaction` 스키마에 설정한 제약 조건과 인덱스는 다음과 같습니다.

| 테이블                        | 제약 조건 및 인덱스                                     | 보장하는 도메인 규칙 및 조회 용도                                        |
| ----------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------ |
| `subscriptions`               | `(subscriber_id, blog_id)` UNIQUE                       | 한 회원이 동일한 블로그를 중복 구독하는 것을 방지                        |
| `subscriptions`               | `(blog_id, subscriber_id)` 인덱스                       | 블로그별 구독자 수 집계 및 구독자 ID 커서 기반 일괄 조회                 |
| `likes`                       | `(account_id, post_id)` UNIQUE                          | 한 회원이 동일한 게시글에 중복으로 좋아요를 누르는 것을 방지             |
| `comments`                    | `(post_id, created_at)` 인덱스                          | 게시글별 댓글 목록의 작성 시각 순 조회                                   |
| `comments`                    | `(parent_id)` 인덱스                                    | 특정 부모 댓글의 하위 답글 존재 여부 확인 및 답글 목록 조회              |
| `comments`                    | `CHECK (removed = (body = ''))`                         | 삭제된 자리 표시(`removed = true`)인 경우에만 본문이 빈 문자열임을 보장  |
| `comments`                    | `CHECK (NOT removed OR parent_id IS NULL)`              | 답글(`parent_id IS NOT NULL`)은 자리 표시로 남지 않고 물리 삭제됨을 보장 |
| `outbox_event`, `inbox_event` | 트랜잭셔널 아웃박스 및 `(event_id, consumer)` PK 인박스 | `CommentAdded` 발행 및 `PostDeleted` 멱등 소비 보장                      |

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 비즈니스 흐름(구독 → 좋아요 → 댓글 작성·수정·삭제 → 외부 상태 변경 처리)과 조회 인터페이스의 계층별 구현입니다.

| 흐름 및 작업                  | 진입점 (`presentation`)              | 응용 서비스 (`application`)                         | 도메인 및 인프라 (`domain` · `infra`)                     |
| ----------------------------- | ------------------------------------ | --------------------------------------------------- | --------------------------------------------------------- |
| 흐름 1. 블로그 구독           | `SubscriptionLikeController`         | `SubscriptionService`                               | `MemberClient`, `PublishingClient` → `Subscription.of`    |
| 흐름 2. 게시글 좋아요         | `SubscriptionLikeController`         | `LikeService`                                       | `MemberClient`, `PublishingClient` → `Like.of`            |
| 흐름 3. 댓글 작성             | `CommentController`                  | `CommentService`                                    | `Comment.write`, `parent.reply` + `OutboxCommentEvents`   |
| 흐름 4. 댓글 수정·삭제        | `CommentController`                  | `CommentService`                                    | `Comment.edit`, `leavePlaceholder`, `requireChangeableBy` |
| 외부 상태 변경 시 데이터 처리 | `PostDeletedConsumer`                | `PostCleanupService`                                | `InteractionInbox` + 댓글·좋아요 일괄 삭제 쿼리           |
| 공개 쿼리 및 HTTP 조회        | `InteractionCountController`, `-api` | `InteractionCountService`, `SubscriberQueryService` | 리포지토리 집계 및 커서 페이징 쿼리                       |

### 흐름 1·2. 블로그 구독·좋아요

구독·좋아요·댓글 쓰기 작업을 수행하기 전에 요청자 계정의 활성 상태와 대상 블로그·게시글의 유효성을 확인하기 위해 `infra/client/`의 번역 계층(ACL)을 호출합니다. 모든 외부 컨텍스트 조회는 **쓰기 트랜잭션을 시작하기 전에** 수행하며, 향후 서비스 분리 시 회원·출판 서비스가 응답하지 않으면 쓰기 요청을 거부합니다(단, 구독 해지·좋아요 취소·댓글 삭제는 외부 컨텍스트를 조회하지 않음).

번역 계층은 외부 `-api` 모듈의 응답을 상호작용 내부 타입(`domain/target`의 `TargetBlog`, `TargetPost`)으로 변환해 반환하며, 레이어드 아키텍처에서 `infra`와 `application`이 함께 참조할 수 있도록 해당 타입은 `domain` 계층에 위치합니다.

| 사전 확인 항목                                    | 구현 위치                       | 호출하는 공개 인터페이스 (`-api`)                                         |
| ------------------------------------------------- | ------------------------------- | ------------------------------------------------------------------------- |
| 회원: 요청자 계정이 `ACTIVE` 상태인지 확인        | `infra/client/MemberClient`     | `member-api`의 `GetAccountStatusQuery`                                    |
| 출판: 블로그 slug로 블로그 ID 및 소유자 확인      | `infra/client/PublishingClient` | `publishing-api`의 `GetBlogReferencesQuery` (`getBlogBySlug`, `getBlogs`) |
| 출판: 게시글 발행 여부 및 소속 블로그·소유자 확인 | `infra/client/PublishingClient` | `publishing-api`의 `GetPostReferencesQuery.getPosts`                      |

사전 확인에서 본인의 블로그이거나 본인의 게시글이면 각각 `409 OWN_BLOG`, `409 OWN_POST` 예외를 던집니다. 검증을 통과하면 트랜잭션을 열어 기존 기록 유무를 확인한 뒤 없을 때만 `Subscription.of(subscriberId, blogId)` 또는 `Like.of(accountId, postId, blogId)`로 저장합니다. 동일한 요청이 동시에 두 번 도달해 두 번째 요청이 DB `UNIQUE` 제약에 걸리면, 응용 서비스는 이를 "이미 처리된 상태"로 간주하여 성공(`204 No Content`)으로 응답합니다.

### 흐름 3·4. 댓글 작성·수정·삭제

개별 댓글(`Comment`)이 독립된 애그리거트이므로, 단일 댓글 내부 상태만으로 판단할 수 있는 규칙은 도메인 메서드가 보장하고 하위 답글 존재 여부 확인이나 이벤트 발행은 `CommentService`가 조율합니다. 댓글 작성이 완료되면 동일한 로컬 트랜잭션 안에서 `OutboxCommentEvents`가 `interaction.outbox_event` 테이블에 `CommentAdded` 이벤트(댓글·게시글·블로그·블로그 소유자·작성자·부모 댓글 작성자 ID 포함)를 기록합니다.

| 규칙 분류                      | 담당 도메인 메서드                                 | 검증 및 상태 변경 내용                                                            |
| ------------------------------ | -------------------------------------------------- | --------------------------------------------------------------------------------- |
| 최상위 댓글 작성 (흐름 3-6, 7) | `Comment.write(postId, blogId, commenterId, body)` | 본문 앞뒤 공백 제거 및 1~1,000자 길이 제한을 검증하여 새 댓글을 생성합니다.       |
| 답글 작성 (흐름 3-4, 5, 6)     | `parent.reply(commenterId, body)`                  | 2단계 답글 시도와 삭제된 자리 표시에 대한 답글 시도를 거부하고 답글을 생성합니다. |
| 댓글 수정 (흐름 4-1, 2, 4)     | `Comment.edit(editorId, body)`                     | 작성자 일치 여부와 삭제 여부를 검증하고 본문을 갱신(`edited = true`)합니다.       |
| 자리 표시 전환 (흐름 4-5)      | `Comment.leavePlaceholder(requesterId)`            | 작성자 검증 후 본문을 비우고 `removed = true`로 전환합니다.                       |
| 물리 삭제 전 검증 (흐름 4-6)   | `Comment.requireChangeableBy(accountId)`           | 작성자 일치 및 미삭제 상태를 확인한 뒤 리포지토리에서 물리 삭제를 수행합니다.     |

#### 부모 댓글·답글 동시성 제어 (`SELECT ... FOR UPDATE`)

부모 댓글과 답글 사이에 외래키가 없으므로, 부모-자식 간 무결성은 비관적 잠금(`Comment.findForUpdate`)으로 보장합니다. 최상위 댓글 하위의 상태를 변경하는 모든 작업은 대상 행을 먼저 잠근 뒤 수행합니다.

특히 답글 삭제 시에는 항상 **자식(답글) 행을 먼저 잠그고 부모 행을 나중에 잠급니다.** 반대로 부모를 먼저 잠근 채 특정 답글 잠금을 기다리는 코드 경로는 존재하지 않으므로 데드락(교착 상태)이 발생하지 않습니다. 또한 자리 표시만 남아 있던 부모 댓글의 마지막 답글을 삭제할 때는, 빈 자리 표시가 순간적으로 노출되지 않도록 **동일 트랜잭션 안에서 부모 자리 표시까지 함께 물리 삭제**합니다.

| 작업 종류        | 잠금 획득 순서                  | 방지하는 동시성 경쟁 상태(Race Condition)                                                              |
| ---------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| 답글 작성        | 부모 댓글 잠금                  | 부모 댓글이 삭제되는 도중에 새 답글이 매달리는 현상 방지                                               |
| 최상위 댓글 삭제 | 해당 최상위 댓글 잠금           | 답글이 없다고 확인한 직후 물리 삭제하기 전에 새 답글이 추가되는 현상 방지                              |
| 답글 삭제        | 해당 답글 잠금 → 부모 댓글 잠금 | 마지막 남은 두 답글이 동시에 삭제되면서 서로 상대 답글이 남아 있다고 판단해 빈 자리를 남기는 현상 방지 |
| 댓글 수정        | 해당 댓글 잠금                  | 삭제 자리 표시로 전환하는 도중에 수정 요청이 본문을 덮어써 되살리는 현상 방지                          |

### 외부 상태 변경 시 데이터 처리 (`PostDeleted`)

출판 컨텍스트에서 게시글이 삭제되어 `publishing.post` 토픽으로 `PostDeleted` 이벤트가 발행되면, `PostDeletedConsumer`(컨슈머 그룹 `interaction.post-deleted`)가 이를 수신하여 `InteractionInbox`(`Inbox.processOnce`) 트랜잭션 안에서 `PostCleanupService`를 호출해 해당 게시글의 댓글과 좋아요를 일괄 삭제합니다. 게시글 자체가 이미 삭제된 상태이므로 개별 댓글의 불변식을 검사할 필요가 없으며, 인박스 기록과 한 트랜잭션으로 묶여 재전달 시에도 멱등하게 처리됩니다.

### 공개 쿼리 인터페이스 (`interaction-api`)

화면 조합 계층이나 알림 컨텍스트에서 상호작용 데이터를 조회할 수 있도록 `interaction-api`에 두 가지 읽기 전용 쿼리를 공개합니다. [01-rules.md](01-rules.md)의 "구독자 목록 비공개" 원칙은 사용자용 화면 API를 열어두지 않는다는 뜻이며, 새 글 알림 발송을 위한 시스템 내부의 `GetSubscribersQuery` 호출은 이에 해당하지 않습니다.

| 공개 인터페이스                                    | 제공 데이터                                                         | 호출 주체 및 용도                                                                |
| -------------------------------------------------- | ------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `GetInteractionCountsQuery`                        | 게시글별 좋아요 수·댓글 수(ID 묶음), 블로그별 구독자 수(ID 묶음)    | 게시글 목록·상세 화면 조합 (반응이 없는 ID는 `0` 반환, 댓글 수는 자리 표시 제외) |
| `GetSubscribersQuery.subscriberIds(blogId, after)` | 특정 블로그의 구독자 ID 목록 (ID 오름차순 1,000건 단위 커서 페이징) | 알림 컨텍스트가 새 글 알림 수신자를 조회할 때만 내부 호출 (HTTP 미공개)          |

### HTTP 엔드포인트

클라이언트에 직접 제공하는 REST API 엔드포인트(`presentation/web`)와 응답 규약입니다. 삭제된 자리 표시 댓글은 응답에서 `removed: true`, 빈 본문(`""`)으로 내려가며 작성자 ID를 노출하지 않습니다.

| HTTP 요청                                    | 응답                                                                            | 대응 흐름 및 규칙  |
| -------------------------------------------- | ------------------------------------------------------------------------------- | ------------------ |
| `PUT /blogs/{slug}/subscription`             | `204 No Content` (이미 구독 중이어도 `204`)                                     | 흐름 1 블로그 구독 |
| `DELETE /blogs/{slug}/subscription`          | `204 No Content` (미구독 상태이거나 존재하지 않는 블로그여도 `204`)             | 흐름 1 구독 해지   |
| `GET /me/subscriptions`                      | 내가 구독한 블로그 목록 (블로그 ID, slug, 구독 시각 — 최근 구독 순)             | 데이터 공개 범위   |
| `GET /me/subscriptions/{slug}`               | `{"subscribed": true}`                                                          | 데이터 공개 범위   |
| `PUT /posts/{id}/like`                       | `204 No Content` (이미 좋아요를 눌렀어도 `204`)                                 | 흐름 2 좋아요      |
| `DELETE /posts/{id}/like`                    | `204 No Content` (누르지 않았거나 비공개 전환된 글이어도 `204`)                 | 흐름 2 좋아요 취소 |
| `GET /me/likes?postIds=`                     | 요청한 게시글 ID(최대 100개) 중 내가 좋아요를 누른 게시글 ID 목록               | 데이터 공개 범위   |
| `GET /interactions/counts?postIds=&blogIds=` | 게시글 좋아요·댓글 수 및 블로그 구독자 수 일괄 조회 (비로그인 허용, 최대 100개) | 데이터 공개 범위   |
| `GET /posts/{id}/comments?cursor=`           | 최상위 댓글 20개씩 오래된 순 + 각 댓글의 답글 전체 (비로그인 허용)              | 데이터 공개 범위   |
| `POST /posts/{id}/comments`                  | `201 Created` 및 작성된 댓글 (`parentId` 지정 시 답글)                          | 흐름 3 댓글 작성   |
| `PATCH /comments/{id}`                       | `200 OK` 및 수정된 댓글 (`edited: true`)                                        | 흐름 4 댓글 수정   |
| `DELETE /comments/{id}`                      | `204 No Content` (하위 답글이 있으면 자리 표시만 남김)                          | 흐름 4 댓글 삭제   |

다건 조회 파라미터(`postIds`, `blogIds`)에 100개를 초과하는 ID를 전달하거나 비즈니스 규칙을 위반했을 때 반환하는 HTTP 상태 코드와 오류 코드는 다음과 같습니다.

| 실패 조건                                                | HTTP 상태 코드 및 오류 코드     |
| -------------------------------------------------------- | ------------------------------- |
| 다건 조회 파라미터 ID 개수(100개) 초과                   | `400 TOO_MANY_IDS`              |
| 요청자 계정이 `ACTIVE`가 아님                            | `403 ACCOUNT_NOT_ACTIVE`        |
| 대상 블로그가 존재하지 않음                              | `404 BLOG_NOT_FOUND`            |
| 대상 게시글이 없거나 발행(`PUBLISHED`) 상태가 아님       | `404 POST_NOT_FOUND`            |
| 본인 블로그 구독 시도 / 본인 게시글 좋아요 시도          | `409 OWN_BLOG` / `409 OWN_POST` |
| 대상 댓글이 존재하지 않음 (이미 물리 삭제된 댓글 포함)   | `404 COMMENT_NOT_FOUND`         |
| 답글의 부모가 해당 게시글의 댓글이 아님                  | `400 INVALID_PARENT`            |
| 답글에 다시 답글 작성을 시도함                           | `400 REPLY_TO_REPLY`            |
| 댓글 본문이 비어 있거나 1,000자를 초과함                 | `400 INVALID_COMMENT`           |
| 댓글 작성자 본인이 아님                                  | `403 NOT_COMMENT_AUTHOR`        |
| 삭제된 자리 표시 댓글을 수정·삭제하거나 답글을 달려고 함 | `409 COMMENT_REMOVED`           |
