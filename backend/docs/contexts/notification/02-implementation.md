# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 레이어드 규칙을 따른다(`presentation`, `application`, `domain`, `infra`).
불변식과 정책을 가르는 기준은 다른 컨텍스트와 같다. 애그리거트 하나나 이벤트 하나만 보고 판단할 수 있으면 도메인, 다른 애그리거트나 다른 컨텍스트를 조회해야 하면 앱이다.

## 애그리거트 경계

| 애그리거트 | 담는 것 | 다른 애그리거트 참조 |
|---|---|---|
| `Notification` | 수신자 ID, 종류, 글 ID, 블로그 ID, 댓글 ID, 쓴 사람 ID, 계기가 된 이벤트 ID, 만든 시각, 읽은 시각 | 모두 ID |

**알림 하나하나가 애그리거트다.** 수신자마다 따로 읽고, 읽음 표시가 다른 알림에 영향을 주지 않는다.
새 글 알림은 구독자 수만큼 한 트랜잭션에서 만든다. 모두 새로 만드는 애그리거트이고, 계기가 된 이벤트 하나를 처리하는 단위다(가입이 계정과 프로필을 함께 만드는 것과 같다).

| 종류 | 코드 | 댓글 ID | 쓴 사람 ID |
|---|---|---|---|
| 새 글 | `NEW_POST` | 없음 | 없음. 블로그 소유자는 화면이 블로그로 조합한다 |
| 내 글의 댓글 | `COMMENT_ON_POST` | 댓글이나 답글 | 있음 |
| 내 댓글의 답글 | `REPLY_TO_COMMENT` | 답글 | 있음 |

## 규칙의 위치

| 01의 규칙 | 위치 | 비고 |
|---|---|---|
| 흐름 1의 1, 2번 | 앱(`NewPostNotifier`) | 구독자는 상호작용에 묻는다 |
| 흐름 2의 1, 2번 | 도메인(`CommentRecipients.of(commenter, blogOwner, parentCommenter)`) | 이벤트 하나에 담긴 값만으로 정한다. 자기 제외와 한 사람에 하나를 여기서 지킨다 |
| 흐름 3의 4, 5번 | 도메인(`Notification.markRead(at)`) | 이미 읽었으면 그대로 둔다 |
| 흐름 3의 3번 | 앱 | 수신자가 아니면 없는 알림과 같게 답한다 |
| 흐름 3의 6번 | 앱 | 한 번의 UPDATE. 알림마다 불러오지 않는다 |
| 보존 기간 | 앱 + 스케줄 | 하루에 한 번, 90일이 지난 알림을 지운다 |

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `notifications(source_event_id, recipient_id)` UNIQUE | 같은 이벤트로 한 사람에게 알림을 두 번 만들지 않는다. 인박스 다음의 두 번째 방어선이다 |
| `notifications(recipient_id, created_at, id)` 인덱스 | 내 알림 목록을 최신순으로 |
| `notifications(recipient_id) WHERE read_at IS NULL` 인덱스 | 안 읽은 수, 모두 읽음 |
| `notifications(created_at)` 인덱스 | 보존 기간이 지난 알림 지우기 |
| CHECK 종류별 필드 | 새 글은 댓글 ID와 쓴 사람 ID가 없고, 댓글 알림은 둘 다 있다 |
| `inbox_event` | 상호작용과 같은 형태. `(event_id, consumer)` 기본키 |

알림은 이벤트를 발행하지 않으므로 `outbox_event`가 없다([architecture/04-persistence-conventions.md](../../architecture/04-persistence-conventions.md)).
외래키는 없다. 알림이 참조하는 것은 모두 다른 컨텍스트에 있다.

## 다른 컨텍스트 호출

| 호출 | 위치 | 내용 |
|---|---|---|
| 상호작용: 블로그의 구독자 | `infra/client/InteractionClient` | `interaction-api`에 새로 공개할 조회. 구독자 ID를 1000개씩 키셋으로 나눠 묻는다 |

알림이 호출해도 되는 상대는 상호작용뿐이다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "허용하는 의존"). 회원과 출판에는 묻지 않는다. 필요한 값은 이벤트가 담고 온다.

구독자는 인박스 트랜잭션을 열기 **전에** 모두 모은다. 같은 이벤트가 다시 오면 조회는 헛일이 되지만, 트랜잭션 안에서 다른 컨텍스트를 기다리지 않는다.
상호작용의 구독자 조회는 화면의 목록이 아니다. 상호작용 01의 "누가 구독했는지 목록으로 보여주지 않는다"는 그대로이고, 이 조회는 HTTP로 공개하지 않는다.

## 이벤트

| 이벤트 | 토픽 | 소비자 그룹 | 하는 일 |
|---|---|---|---|
| `PostPublished` | `publishing.post` | `notification.post-published` | 흐름 1 |
| `CommentAdded` | `interaction.comment` | `notification.comment-added` | 흐름 2 |

소비자는 `presentation/messaging`에 둔다. 같은 토픽의 다른 종류(`PostDeleted`)와 모르는 버전은 넘긴다.
처리는 `platform/messaging`의 `Inbox.processOnce`로 `notification.inbox_event`에 기록한 트랜잭션에서 한다. 알림을 만드는 INSERT는 JDBC 배치로 묶는다.

## HTTP

| 요청 | 응답 | 01 |
|---|---|---|
| `GET /me/notifications?cursor=` | 내 알림 20개씩 최신순. 종류, 글·블로그·댓글·쓴 사람 ID, 만든 시각, 읽음 여부 | 흐름 3의 1번 |
| `GET /me/notifications/unread-count` | `{"count": 3}` | 흐름 3의 2번 |
| `POST /me/notifications/{id}/read` | 204. 이미 읽었어도 204 | 흐름 3의 3~5번 |
| `POST /me/notifications/read-all` | 204 | 흐름 3의 6번 |

모두 로그인이 필요하다. 남의 알림이나 없는 알림을 읽음으로 표시하려 하면 404 `NOTIFICATION_NOT_FOUND`다.

## 단계

| 단계 | 내용 | 완료 조건 |
|---|---|---|
| N0 | 이 문서와 01-rules, README | 결정 항목 확정 |
| N1 | 모듈 골격(`notification-api`, `notification-core`), 레이어드 ArchUnit 규칙, 도메인 | 도메인 규칙마다 테스트 |
| N2 | `notification` 스키마와 롤, 마이그레이션(알림, 인박스) | 빈 DB에 적용, 엔티티 매핑 검사 통과 |
| N3 | `interaction-api`의 구독자 조회, 알림의 번역 계층 | 구독자를 빠짐없이 나눠 받는 테스트 |
| N4 | 소비자 두 개와 인박스 | 글 발행, 댓글, 답글이 Kafka를 거쳐 알림이 되는 테스트(Kafka 컨테이너) |
| N5 | 알림함 HTTP, 보존 기간 | 01 흐름 3의 HTTP 테스트 |
