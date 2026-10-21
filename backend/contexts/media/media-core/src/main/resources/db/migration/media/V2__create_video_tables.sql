-- 영상 (docs/contexts/media/01-rules.md, 영상; 02-implementation.md, 영상).
-- 외래키는 애그리거트 안에만 둔다(video_attachments → videos). 글은 출판의 것이라 ID만 둔다.

CREATE TABLE videos (
    id                     uuid                        NOT NULL,
    uploader_id            uuid                        NOT NULL,
    format                 varchar(10)                 NOT NULL,
    size_bytes             bigint                      NOT NULL,
    file_name              varchar(255)                NOT NULL,
    raw_key                varchar(100)                NOT NULL,
    status                 varchar(20)                 NOT NULL,
    transcoding_started_at timestamp(6) with time zone,
    master_key             varchar(150),
    poster_key             varchar(150),
    duration_ms            bigint,
    width                  integer,
    height                 integer,
    failure_reason         varchar(500),
    unattached_since       timestamp(6) with time zone,
    created_at             timestamp(6) with time zone NOT NULL,
    updated_at             timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_videos PRIMARY KEY (id),
    -- 큐의 알림은 원본 키로 영상을 찾는다
    CONSTRAINT uq_videos_raw_key UNIQUE (raw_key),
    CONSTRAINT ck_videos_format CHECK (format IN ('MP4', 'MOV', 'WEBM', 'MKV')),
    CONSTRAINT ck_videos_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_videos_size CHECK (size_bytes BETWEEN 1 AND 524288000),
    -- 변환 중이면 시작 시각이, 완료면 재생할 것이, 실패면 이유가 있다
    CONSTRAINT ck_videos_processing CHECK (status <> 'PROCESSING' OR transcoding_started_at IS NOT NULL),
    CONSTRAINT ck_videos_completed CHECK (
        status <> 'COMPLETED' OR (master_key IS NOT NULL AND duration_ms IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL)
    ),
    CONSTRAINT ck_videos_failed CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL)
);

-- 정리: 오래 변환 중인 영상
CREATE INDEX ix_videos_status_transcoding_started_at ON videos (status, transcoding_started_at);
-- 정리: 어떤 글에도 쓰이지 않는 영상
CREATE INDEX ix_videos_unattached_since ON videos (unattached_since) WHERE unattached_since IS NOT NULL;

CREATE TABLE video_attachments (
    video_id uuid NOT NULL,
    post_id  uuid NOT NULL,
    CONSTRAINT pk_video_attachments PRIMARY KEY (video_id, post_id),
    CONSTRAINT fk_video_attachments_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

-- 글의 영상 맞추기와 해제, 독자용 조회
CREATE INDEX ix_video_attachments_post_id ON video_attachments (post_id);
