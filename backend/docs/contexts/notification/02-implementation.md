# 알림 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 각 규칙을 어느 계층과 컴포넌트에 배치해 구현했는지 기록합니다.

- 내부 계층 구조는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **레이어드 아키텍처** 규칙(`presentation → application → domain ← infra`)을 따릅니다.
- 단일 애그리거트나 단일 이벤트 페이로드만으로 판단할 수 있으면 도메인(`도메인`), 다른 애그리거트나 외부 컨텍스트를 조회해야 하면 응용 계층(`앱`)에서 처리합니다.

## 애그리거트 경계 및 도메인 모델

알림 컨텍스트는 수신자에게 전달되는 개별 알림 하나하나를 독립된 `Notification` 애그리거트로 관리합니다.

| 애그리거트     | 보유 상태                                                                                                | 타 애그리거트 참조 방식 |
| -------------- | -------------------------------------------------------------------------------------------------------- | ----------------------- |
| `Notification` | 수신자 ID, 알림 종류, 게시글 ID, 블로그 ID, 댓글 ID, 행위자(작성자) ID, 원본 이벤트 ID, 생성 시각, 읽은 시각| 모두 ID 값으로 참조     |

- **개별 알림(`Notification`) 하나하나가 독립된 애그리거트입니다.** 수신자마다 각자 조회하고 읽음 처리를 수행하며, 한 알림의 상태 변경이 다른 알림에 영향을 주지 않습니다.
- 새 글 알림은 한 번의 이벤트 처리 트랜잭션 안에서 구독자 수만큼의 `Notification`을 일괄 생성합니다. 모두 새로 생성(`INSERT`)하는 애그리거트이므로 다른 트랜잭션과 잠금 경합을 일으키지 않습니다.
- `Notification` 엔티티는 변경 가능한 상태가 `read_at`(읽은 시각) 하나뿐이므로 공통 `AuditableEntity`를 상속하지 않고 `created_at`과 `read_at`만 직접 보유합니다.

알림 종류(`NotificationType`)에 따라 보유하는 참조 식별자 구성은 다음과 같이 구분됩니다.

| 알림 종류      | 코드 값            | `comment_id` 보유 여부 | `actor_id`(작성자) 보유 여부                        |
| -------------- | ------------------ | ---------------------- | --------------------------------------------------- |
| 새 글 발행     | `NEW_POST`         | `NULL`                 | `NULL` (블로그 소유자 정보는 화면에서 블로그로 조합)|
| 내 글의 댓글   | `COMMENT_ON_POST`  | 댓글 또는 답글 ID      | 보유                                                |
| 내 댓글의 답글 | `REPLY_TO_COMMENT` | 답글 ID                | 보유                                                |

## 도메인 규칙의 계층별 배치

[01-rules.md](01-rules.md)에 정의된 각 알림 생성 및 알림함 처리 규칙을 담당하는 계층과 주요 클래스 매핑입니다.

| `01-rules.md`의 규칙                           | 담당 위치                                                        | 구현 방식 및 비고                                                                               |
| ---------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| 흐름 1-1, 2 (새 글 알림 생성)                  | 앱 (`NewPostNotifier`) + 도메인 (`Notification.newPost`)         | 상호작용 컨텍스트에서 구독자 ID 목록을 조회한 뒤 구독자별 알림을 생성합니다.                    |
| 흐름 2-1, 2 (댓글·답글 수신자 결정 및 중복 방지)| 도메인 (`CommentRecipients.of(commenter, blogOwner, parentCommenter)`)| 이벤트 하나에 담긴 ID들만으로 수신자와 알림 종류를 계산합니다(부모 댓글 작성자를 먼저 선정하여 블로그 소유자가 중복 수신하지 않도록 보장). |
| 흐름 3-3 (타인 알림 읽음 요청 거부)            | 앱 (`MyNotificationsService`)                                    | 수신자 본인이 아니면 존재하지 않는 알림(`404`)과 동일하게 응답합니다.                           |
| 흐름 3-4, 5 (단건 읽음 처리 및 멱등성)         | 도메인 (`Notification.markRead(at)`)                             | 이미 `read_at`이 있으면 기존 시각을 유지합니다.                                                 |
| 흐름 3-6 (안 읽은 알림 전체 읽음 처리)         | 앱 + `NotificationWriter`                                        | 엔티티를 건별로 로드하지 않고 단일 `UPDATE` 쿼리로 일괄 갱신합니다.                             |
| 90일 경과 알림 자동 삭제                       | 앱 (`NotificationRetention`) + 스케줄러                          | 매일 1회 단일 `DELETE` 쿼리로 만료된 알림을 일괄 삭제합니다.                                    |

## 데이터베이스 스키마 및 제약 조건

`notification` 스키마에서 중복 알림 생성을 방지하고 알림함 조회 성능을 확보하기 위해 설정한 제약 조건과 인덱스입니다.

| 제약 조건 및 인덱스                                             | 보장하는 도메인 규칙 및 용도                                                          |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `notifications(source_event_id, recipient_id)` UNIQUE           | 동일한 원본 이벤트로 한 수신자에게 중복 알림이 생성되는 것을 방지하는 2차 방어선      |
| `notifications(recipient_id, created_at, id)` 인덱스            | 수신자별 알림 목록 최신순 커서(Keyset) 페이징 조회                                    |
| `notifications(recipient_id) WHERE read_at IS NULL` 부분 인덱스 | 미열람 알림 개수 조회 및 전체 읽음 일괄 갱신                                          |
| `notifications(created_at)` 인덱스                              | 90일 보존 기한 경과 알림 일괄 삭제                                                    |
| 알림 종류별 필수 컬럼 CHECK 제약                                | `NEW_POST`는 `comment_id`·`actor_id`가 `NULL`이고, 댓글·답글 알림은 둘 다 `NOT NULL`임을 강제|
| `inbox_event` (`(event_id, consumer)` PK)                       | Kafka 이벤트 멱등 소비 보장                                                           |

- 알림 컨텍스트는 이벤트를 소비만 하고 발행하지 않으므로 `outbox_event` 테이블을 두지 않으며([architecture/04-persistence-conventions.md](../../architecture/04-persistence-conventions.md) 참고), 참조 대상이 모두 외부 컨텍스트에 있으므로 외래키(FK)도 두지 않습니다.
- `notification_migrator` 롤이 스키마를 소유하며 `api_app` 롤은 DML 권한만 가집니다.

## 컨텍스트 간 연동 인터페이스 (`InteractionClient`)

새 글 발행 알림의 수신자를 파악하기 위해 알림 컨텍스트가 상호작용 컨텍스트를 호출하는 아웃바운드 인터페이스입니다.

| 호출 대상                         | 구현 위치                        | 사용하는 공개 인터페이스 (`interaction-api`)                           |
| --------------------------------- | -------------------------------- | ---------------------------------------------------------------------- |
| 상호작용: 특정 블로그의 구독자 목록| `infra/client/InteractionClient` | `GetSubscribersQuery.subscriberIds(blogId, after)` (1,000건 단위 커서) |

- 알림 컨텍스트가 동기 호출할 수 있는 외부 컨텍스트는 상호작용 컨텍스트뿐이며([architecture/03-context-integration.md](../../architecture/03-context-integration.md) 참고), 회원이나 출판 컨텍스트는 호출하지 않습니다.
- `InteractionClient.subscribersOf(blogId)`는 1,000건 미만의 마지막 페이지가 나올 때까지 커서 페이지를 이어 받아 전체 구독자 목록을 수집합니다.
- **트랜잭션 외부에서의 선행 조회**: 구독자 목록 조회는 인박스 DB 트랜잭션을 열기 **전에** 먼저 수행합니다. 이를 통해 외부 컨텍스트 응답을 기다리는 동안 DB 커넥션을 점유하지 않도록 합니다.

## 도메인 이벤트 구독 및 배치 삽입

알림을 생성하기 위해 구독하는 Kafka 도메인 이벤트와 컨슈머 그룹 구성입니다.

| 구독 이벤트     | Kafka 토픽            | 컨슈머 그룹                  | 처리 흐름             |
| --------------- | --------------------- | ---------------------------- | --------------------- |
| `PostPublished` | `publishing.post`     | `notification.post-published`| 흐름 1 (새 글 알림)   |
| `CommentAdded`  | `interaction.comment` | `notification.comment-added` | 흐름 2 (댓글·답글 알림)|

- 각 컨슈머(`PostPublishedConsumer`, `CommentAddedConsumer`)는 `presentation/messaging`에 위치하며, 같은 토픽으로 들어오는 무관한 이벤트(`PostDeleted`)나 알 수 없는 스키마 버전은 무시하고 넘어갑니다.
- 실제 알림 저장은 `platform/messaging`의 `Inbox.processOnce`를 통해 `notification.inbox_event` 기록과 동일한 트랜잭션 안에서 수행됩니다(빈 이름 충돌을 피하기 위해 인박스 빈 이름은 `notificationInbox`로 등록합니다).
- **JPA `save` 대신 JDBC 배치(`NotificationWriter`)로 삽입하는 이유**: 애플리케이션에서 UUID 식별자를 미리 할당한 엔티티를 Spring Data JPA의 `save`로 저장하면 Hibernate가 `merge`로 동작하여 매 행마다 `SELECT`를 먼저 실행합니다. 구독자가 많은 블로그에 새 글이 발행될 때 수백~수천 번의 불필요한 `SELECT` 왕복이 발생하는 것을 막기 위해 `NotificationWriter`에서 500건 단위의 JDBC 배치(`INSERT ... ON CONFLICT DO NOTHING`)로 직접 삽입합니다.

## 알림함 HTTP 엔드포인트 및 보존 기한 스케줄러

회원이 자신의 알림함을 조회하고 읽음 상태로 변경할 때 호출하는 REST API 엔드포인트입니다.

| HTTP 요청                            | 응답                                                                                    | 관련 규칙           |
| ------------------------------------ | --------------------------------------------------------------------------------------- | ------------------- |
| `GET /me/notifications?cursor=`      | 내 알림 20개씩 최신순 조회 (알림 종류, 게시글·블로그·댓글·작성자 ID, 생성 시각, 읽음 여부)| 흐름 3-1 목록 조회  |
| `GET /me/notifications/unread-count` | `{"count": 3}`                                                                          | 흐름 3-2 안 읽은 수 |
| `POST /me/notifications/{id}/read`   | `204 No Content` (이미 읽은 알림이어도 `204`)                                           | 흐름 3-3~5 단건 읽음|
| `POST /me/notifications/read-all`    | `204 No Content`                                                                        | 흐름 3-6 전체 읽음  |

- 모든 엔드포인트는 로그인이 필요하며, 타인의 알림이나 존재하지 않는 알림에 읽음 처리를 요청하면 `404 NOTIFICATION_NOT_FOUND`, 잘못된 커서 값을 전달하면 `400 INVALID_CURSOR`를 반환합니다.
- 알림 목록 페이징(`Notification.findPageFor`)은 키셋(Keyset) 방식으로 21건을 조회해 다음 페이지 존재 여부를 판단합니다.
- **보존 기한 정리 스케줄러**: `NotificationRetentionSchedule`(`presentation/scheduling`)이 매일 `04:41 (Asia/Seoul)`에 `NotificationRetention.removeExpired()`를 호출하여 생성된 지 90일이 지난 알림을 단일 `DELETE` 문으로 삭제합니다. 여러 API 서버 인스턴스(레플리카)에서 동시에 실행되더라도 멱등한 삭제 쿼리이므로 별도의 분산 잠금을 두지 않습니다.
