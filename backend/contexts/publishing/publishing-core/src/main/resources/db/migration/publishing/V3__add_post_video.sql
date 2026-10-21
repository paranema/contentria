-- 글에 첨부한 영상 하나 (docs/contexts/publishing/01-rules.md, 첨부 영상). 영상은 미디어의 것이라 ID만 둔다.
ALTER TABLE posts ADD COLUMN video_id uuid;
