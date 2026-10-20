-- 컨텍스트 간 이벤트의 아웃박스 (docs/architecture/04-persistence-conventions.md, 아웃박스와 인박스).
-- 글을 바꾸는 트랜잭션 안에서 기록하고, 릴레이가 커밋된 행을 Kafka로 보낸다. 릴레이는 아직 없다.

CREATE TABLE outbox_event (
    id             uuid                        NOT NULL,   -- eventId
    aggregate_type varchar(50)                 NOT NULL,   -- 스키마와 함께 토픽을 정한다 (publishing.post)
    aggregate_id   uuid                        NOT NULL,   -- 메시지 키. 같은 글의 이벤트는 순서가 지켜진다
    event_type     varchar(100)                NOT NULL,
    event_version  integer                     NOT NULL,
    payload        jsonb                       NOT NULL,
    occurred_at    timestamp(6) with time zone NOT NULL,
    published_at   timestamp(6) with time zone,            -- 릴레이가 보낸 시각. 비어 있으면 아직 안 보냈다
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

-- 릴레이가 보내지 않은 행을 발생 순서대로 읽는다
CREATE INDEX ix_outbox_event_unpublished ON outbox_event (occurred_at) WHERE published_at IS NULL;
