-- notification 스키마의 첫 테이블 (docs/contexts/notification/02-implementation.md, DB 제약).
-- 외래키는 없다. 알림이 참조하는 회원, 글, 블로그, 댓글은 모두 다른 컨텍스트에 있다.

CREATE TABLE notifications (
    id              uuid                        NOT NULL,
    recipient_id    uuid                        NOT NULL,
    kind            varchar(30)                 NOT NULL,
    post_id         uuid                        NOT NULL,
    blog_id         uuid                        NOT NULL,
    comment_id      uuid,
    actor_id        uuid,
    source_event_id uuid                        NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    read_at         timestamp(6) with time zone,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    -- 같은 이벤트로 한 사람에게 알림을 두 번 만들지 않는다. 인박스 다음의 방어선이다
    CONSTRAINT uq_notifications_event_recipient UNIQUE (source_event_id, recipient_id),
    CONSTRAINT ck_notifications_kind CHECK (kind IN ('NEW_POST', 'COMMENT_ON_POST', 'REPLY_TO_COMMENT')),
    -- 새 글은 댓글과 쓴 사람이 없고, 댓글 알림은 둘 다 있다
    CONSTRAINT ck_notifications_comment_fields CHECK (
        (kind = 'NEW_POST' AND comment_id IS NULL AND actor_id IS NULL)
        OR (kind <> 'NEW_POST' AND comment_id IS NOT NULL AND actor_id IS NOT NULL)
    )
);

-- 내 알림을 최신순으로
CREATE INDEX ix_notifications_recipient_created_at ON notifications (recipient_id, created_at DESC, id DESC);
-- 안 읽은 수, 모두 읽음
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_id) WHERE read_at IS NULL;
-- 보존 기간이 지난 알림 지우기
CREATE INDEX ix_notifications_created_at ON notifications (created_at);

-- 받은 이벤트의 중복 거르기 (PostPublished, CommentAdded). 처리와 같은 트랜잭션에서 기록한다.
-- 기본키에 소비자를 넣어, 이 컨텍스트의 소비자 여럿이 같은 이벤트를 각자 처리할 수 있게 한다
CREATE TABLE inbox_event (
    event_id     uuid                        NOT NULL,
    consumer     varchar(100)                NOT NULL,
    processed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_inbox_event PRIMARY KEY (event_id, consumer)
);
