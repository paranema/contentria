-- media 스키마의 첫 테이블 (docs/contexts/media/02-implementation.md, DB 제약).
-- 외래키는 애그리거트 안에만 둔다(media_attachments → media). 글은 출판의 것이라 ID만 둔다.

CREATE TABLE media (
    id               uuid                        NOT NULL,
    uploader_id      uuid                        NOT NULL,
    format           varchar(10)                 NOT NULL,
    size_bytes       bigint                      NOT NULL,
    file_name        varchar(255)                NOT NULL,
    stored_key       varchar(100)                NOT NULL,
    status           varchar(20)                 NOT NULL,
    unattached_since timestamp(6) with time zone,
    created_at       timestamp(6) with time zone NOT NULL,
    updated_at       timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_media PRIMARY KEY (id),
    -- 한 파일에 기록 하나
    CONSTRAINT uq_media_stored_key UNIQUE (stored_key),
    CONSTRAINT ck_media_format CHECK (format IN ('JPEG', 'PNG', 'WEBP', 'GIF')),
    CONSTRAINT ck_media_status CHECK (status IN ('UPLOADING', 'READY')),
    CONSTRAINT ck_media_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    -- 업로드 중에는 정리 기준 시각이 없다
    CONSTRAINT ck_media_unattached_since CHECK (status = 'READY' OR unattached_since IS NULL)
);

-- 오늘 준비한 양 (회원마다 하루 100MB)
CREATE INDEX ix_media_uploader_id_created_at ON media (uploader_id, created_at);
-- 정리: 완료하지 않은 업로드
CREATE INDEX ix_media_status_created_at ON media (status, created_at);
-- 정리: 어떤 글에도 쓰이지 않는 이미지
CREATE INDEX ix_media_unattached_since ON media (unattached_since) WHERE unattached_since IS NOT NULL;

-- 이미지가 연결된 글. 이미지의 멤버라 이미지와 함께 지워진다
CREATE TABLE media_attachments (
    media_id uuid NOT NULL,
    post_id  uuid NOT NULL,
    CONSTRAINT pk_media_attachments PRIMARY KEY (media_id, post_id),
    CONSTRAINT fk_media_attachments_media FOREIGN KEY (media_id) REFERENCES media (id) ON DELETE CASCADE
);

-- 글의 이미지 맞추기와 해제
CREATE INDEX ix_media_attachments_post_id ON media_attachments (post_id);
