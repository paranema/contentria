-- 상호작용 스키마 (docs/contexts/interaction/02-implementation.md, DB 제약).
-- 외래키는 없다. 구독, 좋아요, 댓글은 모두 다른 애그리거트와 다른 컨텍스트를 ID로만 참조한다. 답글의 부모도 다른 애그리거트다.

CREATE TABLE subscriptions (
    id            uuid                        NOT NULL,
    subscriber_id uuid                        NOT NULL,
    blog_id       uuid                        NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_subscriptions_subscriber_blog UNIQUE (subscriber_id, blog_id)
);

-- 블로그의 구독자 수
CREATE INDEX ix_subscriptions_blog_id ON subscriptions (blog_id);

CREATE TABLE likes (
    id         uuid                        NOT NULL,
    account_id uuid                        NOT NULL,
    post_id    uuid                        NOT NULL,
    blog_id    uuid                        NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_likes PRIMARY KEY (id),
    CONSTRAINT uq_likes_account_post UNIQUE (account_id, post_id)
);

-- 글의 좋아요 수, 글이 지워졌을 때 정리
CREATE INDEX ix_likes_post_id ON likes (post_id);

CREATE TABLE comments (
    id           uuid                        NOT NULL,
    post_id      uuid                        NOT NULL,
    blog_id      uuid                        NOT NULL,
    commenter_id uuid                        NOT NULL,
    parent_id    uuid,
    body         varchar(1000)               NOT NULL,
    edited       boolean                     NOT NULL,
    removed      boolean                     NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    updated_at   timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    -- 지운 자리만 본문이 비어 있다
    CONSTRAINT ck_comments_removed_body CHECK (removed = (body = '')),
    -- 답글은 자리를 남기지 않고 실제로 지운다. 자리는 답글이 달린 최상위 댓글에만 생긴다
    CONSTRAINT ck_comments_placeholder_top_level CHECK (NOT removed OR parent_id IS NULL)
);

-- 글의 댓글을 시간순으로, 글이 지워졌을 때 정리
CREATE INDEX ix_comments_post_id_created_at ON comments (post_id, created_at);
-- 답글이 있는지, 답글 목록
CREATE INDEX ix_comments_parent_id ON comments (parent_id);

-- 컨텍스트 간 이벤트의 아웃박스 (CommentAdded). 형태는 publishing.outbox_event와 같다
CREATE TABLE outbox_event (
    id             uuid                        NOT NULL,
    aggregate_type varchar(50)                 NOT NULL,
    aggregate_id   uuid                        NOT NULL,
    event_type     varchar(100)                NOT NULL,
    event_version  integer                     NOT NULL,
    payload        jsonb                       NOT NULL,
    occurred_at    timestamp(6) with time zone NOT NULL,
    published_at   timestamp(6) with time zone,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX ix_outbox_event_unpublished ON outbox_event (occurred_at) WHERE published_at IS NULL;

-- 받은 이벤트의 중복 거르기 (PostDeleted). 처리와 같은 트랜잭션에서 기록한다.
-- 기본키에 소비자를 넣어, 이 컨텍스트의 소비자 여럿이 같은 이벤트를 각자 처리할 수 있게 한다
CREATE TABLE inbox_event (
    event_id     uuid                        NOT NULL,
    consumer     varchar(100)                NOT NULL,
    processed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_inbox_event PRIMARY KEY (event_id, consumer)
);
