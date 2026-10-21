-- analytics 스키마의 첫 테이블 (docs/contexts/analytics/02-implementation.md, DB 제약).
-- 외래키는 없다. 블로그와 글은 출판의 것이다. IP는 어디에도 남기지 않는다.

CREATE TABLE visits (
    id            uuid                        NOT NULL,
    blog_id       uuid                        NOT NULL,
    post_id       uuid,
    visitor_key   varchar(64)                 NOT NULL,
    referrer_host varchar(255),
    visited_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_visits PRIMARY KEY (id),
    -- HMAC-SHA256의 16진수. 원문이 들어오지 않게 한 번 더 막는다
    CONSTRAINT ck_visits_visitor_key CHECK (visitor_key ~ '^[0-9a-f]{64}$')
);

-- 30분 안의 같은 방문 찾기
CREATE INDEX ix_visits_dedup ON visits (blog_id, post_id, visitor_key, visited_at);
-- 블로그의 오늘 숫자
CREATE INDEX ix_visits_blog_id_visited_at ON visits (blog_id, visited_at);
-- 하루치 집계, 90일 지난 기록 지우기
CREATE INDEX ix_visits_visited_at ON visits (visited_at);

-- 하루의 블로그 전체(post_id IS NULL)와 글마다의 수. 집계가 그날 값을 통째로 바꾼다
CREATE TABLE daily_statistics (
    id            uuid                        NOT NULL DEFAULT gen_random_uuid(),
    blog_id       uuid                        NOT NULL,
    post_id       uuid,
    stat_date     date                        NOT NULL,
    visitors      bigint                      NOT NULL,
    views         bigint                      NOT NULL,
    aggregated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_daily_statistics PRIMARY KEY (id),
    -- 블로그 전체 행도 하루에 하나다. 다시 집계하면 같은 행을 바꾼다
    CONSTRAINT uq_daily_statistics_blog_post_date UNIQUE NULLS NOT DISTINCT (blog_id, post_id, stat_date),
    -- 방문자 한 명은 조회 한 번 이상이다
    CONSTRAINT ck_daily_statistics_counts CHECK (visitors >= 0 AND views >= visitors)
);

-- 블로그의 추이, 누적, 인기 글
CREATE INDEX ix_daily_statistics_blog_id_stat_date ON daily_statistics (blog_id, stat_date);
-- 글마다 누적 조회수
CREATE INDEX ix_daily_statistics_post_id ON daily_statistics (post_id) WHERE post_id IS NOT NULL;
