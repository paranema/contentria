# 알림 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 새 글 알림, 댓글·답글 알림, 알림함 조회·읽음 처리 규칙을 실제 코드 계층과 데이터베이스에 어떻게 배치해 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

알림 컨텍스트(`contexts/notification`)는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **레이어드 아키텍처**(`presentation → application → domain ← infra`)를 따릅니다. 단일 애그리거트나 단일 이벤트 페이로드만으로 판단할 수 있는 규칙은 도메인(`도메인`)에서 처리하고, 다른 애그리거트나 외부 컨텍스트를 조회해야 판단할 수 있는 규칙은 응용 계층(`앱`)에서 조율합니다.

### 스프링 설정 구성

스프링 설정은 역할에 따라 네 클래스로 분리되어 있으며, 현재는 모두 API 서버(`apps/api`)에서 로드됩니다.

| 설정 클래스                           | 등록 대상 컴포넌트                                                                 | 로드 프로세스 |
| ------------------------------------- | ---------------------------------------------------------------------------------- | ------------- |
| `NotificationCoreConfiguration`       | 엔티티, 리포지토리, 응용 서비스, 외부 클라이언트(`infra/client`), JDBC 배치 기록기 | `apps/api`    |
| `NotificationMessagingConfiguration`  | Kafka 이벤트 컨슈머 및 멱등 인박스(`presentation/messaging`, `notificationInbox`)  | `apps/api`    |
| `NotificationWebConfiguration`        | 알림함 조회·읽음 처리 REST 컨트롤러(`presentation/web`)                            | `apps/api`    |
| `NotificationSchedulingConfiguration` | 90일 경과 알림 자동 삭제 스케줄러(`presentation/scheduling`)                       | `apps/api`    |

## 데이터 모델

### 애그리거트 구성

알림 컨텍스트는 수신자에게 전달되는 개별 알림 하나하나를 독립된 `Notification` 애그리거트로 관리합니다. 수신자마다 각자 알림을 조회하고 읽음 처리를 수행하며 한 알림의 상태 변경이 다른 알림에 영향을 주지 않으므로, 새 글 발행 시 구독자 수만큼의 `Notification`을 한 트랜잭션에서 일괄 생성(`INSERT`)하더라도 다른 트랜잭션과 잠금 경합이 발생하지 않습니다.

`Notification` 엔티티는 변경 가능한 상태가 `read_at`(읽은 시각) 하나뿐이므로 공통 `AuditableEntity`를 상속하지 않고 `created_at`과 `read_at`만 직접 보유합니다. 알림 종류(`NotificationType`)에 따라 보유하는 참조 식별자 구성은 다음과 같습니다.

| 알림 종류      | 코드 값            | 보유하는 공통 상태                                              | `comment_id` 보유 여부 | `actor_id`(작성자) 보유 여부                         |
| -------------- | ------------------ | --------------------------------------------------------------- | ---------------------- | ---------------------------------------------------- |
| 새 글 발행     | `NEW_POST`         | 수신자 ID, 게시글 ID, 블로그 ID, 원본 이벤트 ID, 생성·읽은 시각 | `NULL`                 | `NULL` (블로그 소유자 정보는 화면에서 블로그로 조합) |
| 내 글의 댓글   | `COMMENT_ON_POST`  | 수신자 ID, 게시글 ID, 블로그 ID, 원본 이벤트 ID, 생성·읽은 시각 | 댓글 또는 답글 ID      | 보유                                                 |
| 내 댓글의 답글 | `REPLY_TO_COMMENT` | 수신자 ID, 게시글 ID, 블로그 ID, 원본 이벤트 ID, 생성·읽은 시각 | 답글 ID                | 보유                                                 |

### 데이터베이스 스키마

알림 컨텍스트는 이벤트를 소비만 하고 발행하지 않으므로 `outbox_event` 테이블을 두지 않으며([architecture/04-persistence-conventions.md](../../architecture/04-persistence-conventions.md) 참고), 참조 대상이 모두 외부 컨텍스트에 있으므로 외래키(FK)도 두지 않습니다. `notification_migrator` 롤이 스키마를 소유하며 `api_app` 롤은 DML 권한만 가집니다. 중복 알림 방지와 알림함 조회 성능을 위해 설정한 제약 조건과 인덱스는 다음과 같습니다.

| 테이블          | 제약 조건 및 인덱스                                | 보장하는 도메인 규칙 및 조회 용도                                               |
| --------------- | -------------------------------------------------- | ------------------------------------------------------------------------------- |
| `notifications` | `(source_event_id, recipient_id)` UNIQUE           | 동일한 원본 이벤트로 한 수신자에게 중복 알림이 생성되는 것을 막는 2차 방어선    |
| `notifications` | 알림 종류별 필수 컬럼 `CHECK` 제약                 | `NEW_POST`는 `comment_id`·`actor_id`가 `NULL`, 댓글·답글 알림은 `NOT NULL` 강제 |
| `notifications` | `(recipient_id, created_at, id)` 인덱스            | 수신자별 알림 목록 최신순 커서(Keyset) 페이징 조회                              |
| `notifications` | `(recipient_id) WHERE read_at IS NULL` 부분 인덱스 | 미열람 알림 개수 조회 및 전체 읽음 일괄 갱신                                    |
| `notifications` | `(created_at)` 인덱스                              | 90일 보존 기한 경과 알림 일괄 삭제                                              |
| `inbox_event`   | `(event_id, consumer)` PK                          | Kafka 도메인 이벤트 멱등 소비 보장 (`notificationInbox`)                        |

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 세 가지 비즈니스 흐름과 보존 기한 정리 작업의 계층별 구현입니다.

| 흐름 및 작업                  | 진입점 (`presentation`)         | 응용 계층 (`application`) | 도메인 및 인프라 (`domain` · `infra`)                              |
| ----------------------------- | ------------------------------- | ------------------------- | ------------------------------------------------------------------ |
| 흐름 1. 새 글 알림 생성       | `PostPublishedConsumer`         | `NewPostNotifier`         | `InteractionClient` → `Notification.newPost`, `NotificationWriter` |
| 흐름 2. 댓글·답글 알림 생성   | `CommentAddedConsumer`          | `CommentNotifier`         | `CommentRecipients.of`, `NotificationWriter`                       |
| 흐름 3. 알림함 조회·읽음 처리 | `MyNotificationsController`     | `MyNotificationsService`  | `Notification.markRead`, `NotificationWriter`                      |
| 보존 기한 만료 알림 정리      | `NotificationRetentionSchedule` | `NotificationRetention`   | 만료 알림 일괄 `DELETE` 쿼리                                       |

### 흐름 1. 새 글 알림 생성

출판 컨텍스트의 `publishing.post` 토픽에서 `PostPublished` 이벤트가 도착하면 `PostPublishedConsumer`(컨슈머 그룹 `notification.post-published`)가 이를 수신합니다(같은 토픽의 `PostDeleted` 이벤트나 알 수 없는 버전은 무시).

1. **구독자 목록 선행 조회 (`InteractionClient`)**: 인박스 DB 트랜잭션을 열기 **전에** `infra/client/InteractionClient`를 통해 `interaction-api`의 `GetSubscribersQuery.subscriberIds(blogId, after)`를 호출합니다. 1,000건 미만의 마지막 페이지가 나올 때까지 커서 페이지를 이어 받아 전체 구독자 목록을 수집하며, 외부 응답을 기다리는 동안 DB 커넥션을 점유하지 않습니다(알림 컨텍스트가 동기 호출하는 외부 컨텍스트는 상호작용 컨텍스트뿐입니다).
2. **멱등 인박스 처리 및 JDBC 배치 삽입 (`NotificationWriter`)**: `notificationInbox`(`Inbox.processOnce`) 트랜잭션 안에서 구독자별 `Notification.newPost`를 생성해 저장합니다. 이때 애플리케이션에서 UUID 식별자를 미리 할당한 엔티티를 Spring Data JPA의 `save`로 저장하면 Hibernate가 `merge`로 동작해 매 행마다 `SELECT`를 먼저 실행하므로, 수백~수천 건의 불필요한 왕복을 막기 위해 `NotificationWriter`에서 500건 단위의 JDBC 배치(`INSERT ... ON CONFLICT DO NOTHING`)로 직접 삽입합니다.

### 흐름 2. 댓글·답글 알림 생성

상호작용 컨텍스트의 `interaction.comment` 토픽에서 `CommentAdded` 이벤트가 도착하면 `CommentAddedConsumer`(컨슈머 그룹 `notification.comment-added`)가 이를 수신합니다.

이벤트 페이로드 하나에 댓글 작성자, 블로그 소유자, 부모 댓글 작성자 ID가 모두 담겨 있으므로 외부 컨텍스트를 조회하지 않고 도메인 모델인 `CommentRecipients.of(commenter, blogOwner, parentCommenter)`만으로 수신자와 알림 종류를 즉시 계산합니다. 부모 댓글 작성자를 먼저 선정한 뒤 블로그 소유자가 이미 포함되었는지 확인하여 동일인에게 중복 알림이 생성되지 않도록 보장하며, 계산된 알림은 `notificationInbox` 트랜잭션 안에서 `NotificationWriter`로 저장합니다.

### 흐름 3. 알림함 조회·읽음 처리

회원이 자신의 알림함을 조회하고 읽음 상태로 변경할 때 호출하는 REST API 엔드포인트(`presentation/web`)와 처리 방식입니다.

| HTTP 요청                            | 응답                                                                                       | 대응 규칙 및 구현 방식                                                                           |
| ------------------------------------ | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| `GET /me/notifications?cursor=`      | 내 알림 20개씩 최신순 조회 (알림 종류, 게시글·블로그·댓글·작성자 ID, 생성 시각, 읽음 여부) | 흐름 3-1 — 키셋(Keyset) 방식으로 21건을 조회해 다음 페이지 존재 여부를 판단합니다.               |
| `GET /me/notifications/unread-count` | `{"count": 3}`                                                                             | 흐름 3-2 — 부분 인덱스를 사용해 미열람 알림 개수를 조회합니다.                                   |
| `POST /me/notifications/{id}/read`   | `204 No Content` (이미 읽은 알림이어도 `204`)                                              | 흐름 3-3~5 — `Notification.markRead(at)`로 최초 읽은 시각을 기록합니다.                          |
| `POST /me/notifications/read-all`    | `204 No Content`                                                                           | 흐름 3-6 — 엔티티를 건별로 로드하지 않고 `NotificationWriter`의 단일 `UPDATE`로 일괄 갱신합니다. |

모든 엔드포인트는 로그인이 필요하며, 타인의 알림이나 존재하지 않는 알림에 단건 읽음 처리를 요청하면 `404 NOTIFICATION_NOT_FOUND`, 잘못된 커서 값을 전달하면 `400 INVALID_CURSOR`를 반환합니다.

### 보존 기한 만료 알림 정리

`NotificationRetentionSchedule`(`presentation/scheduling`)이 매일 `04:41 (Asia/Seoul)`에 `NotificationRetention.removeExpired()`를 호출하여 생성된 지 90일이 지난 알림을 단일 `DELETE` 문으로 삭제합니다. 여러 API 서버 인스턴스(레플리카)에서 동시에 실행되더라도 멱등한 삭제 쿼리이므로 별도의 분산 잠금을 두지 않습니다.
