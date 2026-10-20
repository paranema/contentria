-- 출판 스키마 (docs/contexts/publishing/02-implementation.md, DB 제약).
-- 외래키는 애그리거트 안에만 둔다: categories → category_trees, categories → categories(부모), post_slugs → posts.
-- 애그리거트 사이(posts → blogs, posts → categories, category_trees → blogs)에는 두지 않는다.

CREATE TABLE blogs (
    id          uuid                        NOT NULL,
    owner_id    uuid                        NOT NULL,
    slug        varchar(30)                 NOT NULL,
    title       varchar(50)                 NOT NULL,
    description varchar(300),
    created_at  timestamp(6) with time zone NOT NULL,
    updated_at  timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_blogs PRIMARY KEY (id),
    CONSTRAINT uq_blogs_slug UNIQUE (slug)
);

-- 회원당 블로그 수를 셀 때와 내 블로그 목록
CREATE INDEX ix_blogs_owner_id ON blogs (owner_id);

-- 블로그마다 하나. id는 블로그 ID와 같은 값이다
CREATE TABLE category_trees (
    id         uuid                        NOT NULL,
    version    bigint                      NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_category_trees PRIMARY KEY (id)
);

-- 트리는 한 번에 저장한다. Hibernate가 삭제보다 삽입을, 부모보다 자식을 먼저 실행할 수 있어서
-- slug 고유 제약과 부모 외래키는 커밋할 때 검사한다.
CREATE TABLE categories (
    id            uuid                        NOT NULL,
    tree_id       uuid                        NOT NULL,
    name          varchar(30)                 NOT NULL,
    slug          varchar(50)                 NOT NULL,
    parent_id     uuid,
    display_order integer                     NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_tree FOREIGN KEY (tree_id) REFERENCES category_trees (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_categories_tree_slug UNIQUE (tree_id, slug) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_categories_display_order CHECK (display_order >= 0)
);

CREATE TABLE posts (
    id              uuid                        NOT NULL,
    blog_id         uuid                        NOT NULL,
    category_id     uuid,
    title           varchar(150)                NOT NULL,
    body            text                        NOT NULL,
    summary         varchar(300)                NOT NULL,
    summary_written boolean                     NOT NULL,
    status          varchar(20)                 NOT NULL,
    published_at    timestamp(6) with time zone,
    slug            varchar(100)                NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_posts PRIMARY KEY (id),
    CONSTRAINT ck_posts_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'PRIVATE')),
    -- 임시저장 글만 발행 시각이 없다. 한 번 발행한 글은 비공개여도 발행 시각을 가진다
    CONSTRAINT ck_posts_published_at CHECK ((status = 'DRAFT') = (published_at IS NULL))
);

-- 블로그의 글 목록
CREATE INDEX ix_posts_blog_id_published_at ON posts (blog_id, published_at DESC);
-- 카테고리를 지우기 전에 글이 있는지 확인
CREATE INDEX ix_posts_category_id ON posts (category_id);
-- 홈의 최근 공개 글
CREATE INDEX ix_posts_recent_public ON posts (published_at DESC) WHERE status = 'PUBLISHED';

-- 글이 가진 적 있는 slug. 현재 slug(posts.slug)도 들어 있다.
-- (blog_id, slug) 고유 제약이 블로그 안에서 현재와 옛 slug를 합쳐 겹치지 않게 한다. 주소로 글을 찾을 때도 이 인덱스를 쓴다.
CREATE TABLE post_slugs (
    id         uuid                        NOT NULL,
    post_id    uuid                        NOT NULL,
    blog_id    uuid                        NOT NULL,
    slug       varchar(100)                NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_post_slugs PRIMARY KEY (id),
    CONSTRAINT fk_post_slugs_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT uq_post_slugs_blog_slug UNIQUE (blog_id, slug)
);

CREATE INDEX ix_post_slugs_post_id ON post_slugs (post_id);
