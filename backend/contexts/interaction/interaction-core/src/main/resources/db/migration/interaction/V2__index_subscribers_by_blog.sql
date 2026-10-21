-- 블로그의 구독자를 ID 순으로 나눠 읽는다 (알림의 새 글 알림, notification/02-implementation.md).
-- 블로그별 구독자 수도 이 인덱스의 앞부분으로 센다. 그래서 blog_id 하나만의 인덱스는 지운다
CREATE INDEX ix_subscriptions_blog_id_subscriber_id ON subscriptions (blog_id, subscriber_id);
DROP INDEX ix_subscriptions_blog_id;
