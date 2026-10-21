# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 레이어드 규칙을 따른다(`presentation`, `application`, `domain`, `infra`).
불변식과 정책을 가르는 기준은 다른 컨텍스트와 같다. 애그리거트 하나만 보고 판단할 수 있으면 도메인, 다른 기록이나 다른 컨텍스트를 봐야 하면 앱이다. 분석은 규칙 대부분이 기록을 조회해야 판단하므로 앱에 있다.

## 애그리거트 경계

| 애그리거트 | 담는 것 | 바뀌는 때 |
|---|---|---|
| `Visit` | 블로그 ID, 글 ID(블로그 홈이면 없음), 방문자 키, 유입 도메인, 방문 시각 | 만든 뒤 바뀌지 않는다. 90일 뒤 지운다 |
| `DailyStatistics` | 블로그 ID, 글 ID(블로그 전체면 없음), 날짜, 방문자 수, 조회수 | 집계가 그날의 값을 통째로 바꾼다 |

| 도메인 | 하는 일 |
|---|---|
| `Visit.record(blogId, postId, visitorKey, referrer, at)` | 방문자 키는 소문자 16진수 64자(HMAC-SHA256)만 받는다. IP 같은 원문이 들어오면 거부한다 |
| 유입 도메인 | `http`, `https` 주소의 호스트만 소문자로. 경로, 검색어, 포트, 계정 정보는 버리고, 앱 주소나 주소가 아닌 값은 남기지 않는다 |

두 애그리거트 사이에 참조는 없다. 일별 통계는 방문 기록에서 계산한 결과다.
일별 통계는 엔티티로 다루지 않고 집계 쿼리로 넣는다. 한 번에 수천 행을 다시 쓰는 일이라 엔티티를 하나씩 불러오지 않는다.

## 규칙의 위치

| 01의 규칙 | 위치 | 비고 |
|---|---|---|
| 흐름 1의 1~3번 | 앱 + `infra/client/PublishingClient` | 출판에 블로그 소유자와 글의 블로그, 공개 여부를 묻는다 |
| 흐름 1의 4번 | 앱 | 같은 방문자 키, 블로그, 글의 30분 안 기록이 있는지 조회한다 |
| 흐름 1의 5번 | 도메인(`Visit.record`) | 유입 주소에서 도메인만 남긴다 |
| 방문자 키 | 앱(`VisitorKeys`) | HMAC-SHA256(비밀 키, 날짜 + IP + User-Agent). 비밀 키는 설정(`contentria.analytics.visitor-key-secret`)이다 |
| 흐름 2 | 앱 + 배치 진입점 | `GROUPING SETS`로 블로그 전체와 글마다를 한 번에 세고, `ON CONFLICT DO UPDATE`로 그날 값을 바꾼다 |
| 흐름 3, 4 | 앱 | 오늘은 방문 기록에서, 지난날은 일별 통계에서 |

동시에 온 같은 방문은 둘 다 30분 확인을 지나 두 번 기록될 수 있다. 조회수는 근사치이고, 이를 막으려고 잠그지 않는다.

## 다른 컨텍스트 호출

| 호출 | 위치 | 내용 |
|---|---|---|
| 출판: 블로그의 소유자 | `infra/client/PublishingClient` | `publishing-api`의 `GetBlogReferencesQuery.getBlogs` |
| 출판: 글의 블로그와 공개 여부 | 같은 곳 | `GetPostReferencesQuery.getPosts` |

분석이 출판을 호출하는 것은 이번에 새로 생긴 방향이다([strategic/04-context-map.md](../../strategic/04-context-map.md)). 출판은 분석을 모르므로 의존은 한 방향이다.
출판이 응답하지 않으면(분리 후) 그 방문은 버린다. 방문 하나를 잃는 것이 맞지 않는 방문을 세는 것보다 낫다.

블로그 대시보드는 분석이 아니라 `apps/api`가 조합한다(아래 "조회 조합"). 분석은 대시보드를 위해 출판을 부르지 않는다.

## 공개 조회 (`analytics-api`)

| 포트 | 내용 | 쓰는 곳 |
|---|---|---|
| `GetBlogTrafficQuery.summary(blogId)` | 오늘, 어제, 누적의 방문자 수와 조회수 | 블로그 대시보드 조합 |
| `GetBlogTrafficQuery.trend(blogId, from, to)` | 날마다의 방문자 수와 조회수. 방문이 없던 날은 0 | 위와 같다 |
| `GetBlogTrafficQuery.popularPosts(blogId, from, to, limit)` | 글 ID와 기간의 조회수 | 위와 같다 |
| `GetViewCountsQuery.viewCounts(postIds)` | 글마다 누적 조회수. 묻는 ID마다 값이 있고 없으면 0 | 글 목록과 상세의 조합(나중에) |

분석은 블로그 ID와 글 ID만 안다. 블로그가 누구의 것인지 확인하는 것은 조합하는 쪽이다.

| 구현 | 위치 |
|---|---|
| 두 포트 | `application/statistics/TrafficQueryService`. 읽기 전용 트랜잭션 |
| SQL | `infra/persistence/TrafficSql`. 오늘은 `visits`에서, 오늘 전은 `daily_statistics`에서만 읽어 한 날을 두 번 세지 않는다 |
| 기간 | `trend(blogId, days)`, `popularPosts(blogId, days, limit)`는 오늘로 끝나는 날 수를 받는다. "오늘"은 분석의 한국 날짜다. 조합하는 쪽이 날짜를 계산하지 않는다 |

인기 글은 그사이 지워진 글이 빠지면 모자랄 수 있어, 조합은 분석에 보여줄 수의 두 배를 묻고 출판에 제목이 있는 것만 남긴다.

## 조회 조합 (`apps/api`)

블로그 대시보드는 `apps/api`에 처음으로 생기는 조회 조합이다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "예시: 글 상세를 조합한다").

| 요청 | 하는 일 |
|---|---|
| `GET /me/blogs/{slug}/dashboard?range=2weeks` | 출판에서 블로그와 소유자를 받아 요청자와 맞춰 본다. 분석에서 요약, 추이, 인기 글 ID를 받고, 출판에서 인기 글의 제목과 slug, 발행한 글 수를 받아 한 응답으로 묶는다 |

출판의 `-api`에 `GetPostOverviewsQuery`(`getTitles(postIds)`, `countPublished(blogId)`)를 새로 공개했다. 대시보드는 블로그 소유자만 보므로 공개하지 않은 글의 제목도 준다. 기존 `GetPostReferencesQuery`에 더하지 않은 것은 그 포트를 구현한 다른 컨텍스트의 테스트 가짜를 깨지 않기 위해서다.

| 구현 | 위치 |
|---|---|
| 조합 | `apps/api`의 `composition/dashboard/BlogDashboard`, `BlogDashboardController` |
| 기간 | `range`는 `2weeks`(기본), `30days`, `90days`. 다른 값은 400 `INVALID_REQUEST` |
| 남의 블로그 | 없는 블로그와 같은 404 `BLOG_NOT_FOUND` |

## HTTP (분석)

| 요청 | 응답 |
|---|---|
| `POST /analytics/visits` `{blogId, postId?, referrer?}` | 204. 버린 방문도 204. 로그인하지 않아도 된다 |

| 구성 | 위치 |
|---|---|
| 유스케이스 | `application/VisitRecorder`. 출판에 묻고 나서 짧은 트랜잭션에서 30분 확인과 저장을 한다 |
| 방문자 키 | `application/VisitorKeys`. 비밀 키는 32자 이상이고 기본값이 없다(`ANALYTICS_VISITOR_KEY_SECRET`). 날짜, IP, User-Agent 사이에 NUL을 넣어 경계를 옮긴 입력이 같은 키가 되지 않게 한다 |
| 30분 확인 | 글 방문과 블로그 홈 방문을 두 파생 쿼리로 나눈다. 글 ID가 비었다는 것은 "모든 글"이 아니라 "홈"이다 |
| 출판 번역 계층 | `infra/client/PublishingClient`. 빈 이름은 `analyticsPublishingClient`다. 상호작용에도 같은 이름의 클래스가 있어, 한 프로세스에서 기본 빈 이름이 겹친다 |
| 웹 | `presentation/web/VisitController`. 로그인한 독자면 `@AuthenticationPrincipal`로 계정 ID를 받고, 아니면 비어 있다 |

IP는 요청의 원격 주소에서 받는다. 프록시 뒤에서는 앱이 전달 헤더를 처리해 원격 주소를 바로잡는다(`server.forward-headers-strategy`). 분석은 헤더를 직접 읽지 않는다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `visits(blog_id, post_id, visitor_key, visited_at)` 인덱스 | 30분 안의 같은 방문 찾기 |
| `visits(visited_at)` 인덱스 | 하루치 집계, 오늘의 숫자, 90일 지난 기록 지우기 |
| `daily_statistics(blog_id, post_id, stat_date)` UNIQUE NULLS NOT DISTINCT | 하루, 블로그, 글마다 한 행. 블로그 전체 행(`post_id IS NULL`)도 겹치지 않는다. 다시 집계하면 같은 행을 바꾼다 |
| `daily_statistics(blog_id, stat_date)` 인덱스 | 대시보드의 추이와 누적 |
| CHECK 수는 0 이상 | |

마이그레이션은 `analytics-core/src/main/resources/db/migration/analytics/V1__create_analytics_tables.sql`이다. `analytics_migrator`가 스키마를 소유하고, `api_app`(방문 기록, 조회)과 `batch_app`(집계, 지우기)은 DML만 가진다.
방문자 키는 CHECK(`^[0-9a-f]{64}$`)로도 막는다. 일별 통계는 엔티티가 없어 매핑 검사 대신 마이그레이션 테스트가 제약과 `ON CONFLICT` 갱신을 확인한다.

외래키는 없다. 블로그와 글은 출판의 것이다. 분석은 이벤트를 주고받지 않으므로 아웃박스와 인박스가 없다.

## 배치

| 잡 | 하는 일 |
|---|---|
| `analyticsDailyStatistics` | 최근 3일(어제, 그제, 그끄제)을 다시 집계하고, 90일 지난 방문 기록을 지운다 |

`apps/batch`가 띄운다. `batch_app` 롤에 `analytics` 스키마의 DML을 더한다.

| 구성 | 위치 |
|---|---|
| 유스케이스 | `application/statistics/DailyStatisticsService`. 끝난 최근 3일을 날마다 트랜잭션 하나에서 다시 집계하고, 90일 지난 방문을 지운다 |
| SQL | `infra/persistence/DailyStatisticsSql`. `GROUPING SETS ((blog_id, post_id), (blog_id))`에 `HAVING GROUPING(post_id) = 1 OR post_id IS NOT NULL`을 건다. 블로그 홈 방문(`post_id IS NULL`)이 따로 묶이면 블로그 전체 행과 같은 키가 되어, 한 문장 안에서 같은 행을 두 번 바꾸려다 실패한다 |
| 잡 | `presentation/batch/DailyStatisticsJob`(`analyticsDailyStatistics`). 단계 자체는 트랜잭션이 없다 |

### 설정

| 설정 | 담는 것 | 불러오는 곳 |
|---|---|---|
| `AnalyticsCoreConfiguration` | 엔티티, 리포지토리, 집계와 조회(`application/statistics`, `infra/persistence`). DB만 쓴다 | apps/api, apps/batch |
| `AnalyticsWebConfiguration` | 방문 기록 전체(`presentation/web`, `application/visit`, `infra/client`, 방문자 키 비밀 키) | apps/api |
| `AnalyticsBatchConfiguration` | 잡(`presentation/batch`) | apps/batch |

방문 기록은 출판에 묻고 비밀 키가 있어야 한다. 웹으로만 들어오는 흐름이라 그 전부를 웹 설정에 두었다. 배치는 출판과 비밀 키 없이 뜬다.

`apps/batch`에 잡이 둘(`mediaImageCleanup`, `analyticsDailyStatistics`)이 되어, 일정은 잡 이름(`--spring.batch.job.name` 또는 `BATCH_JOB_NAME`)을 꼭 넘겨야 한다. 이름 없이 띄우면 Spring Boot가 어느 잡을 돌릴지 정하지 못해 실패한다.

## 단계

| 단계 | 내용 | 완료 조건 |
|---|---|---|
| A0 | 이 문서와 01-rules, README | 결정 항목 확정 |
| A1 | 모듈 골격(`analytics-api`, `analytics-core`), 레이어드 ArchUnit 규칙, 도메인(`Visit`) | 도메인 규칙마다 테스트 |
| A2 | `analytics` 스키마와 롤, 마이그레이션 | 빈 DB에 적용, 엔티티 매핑 검사 통과 |
| A3 | 방문 기록: 출판 번역 계층, 방문자 키, 30분 중복 제외, HTTP | 소유자 방문, 비공개 글, 남의 블로그 글, 30분 안 재방문을 버리는 HTTP 테스트 |
| A4 | 일별 집계와 보관 잡(`apps/batch`) | 블로그 전체와 글마다의 수, 다시 집계해도 같은 결과, 90일 경계 |
| A5 | `analytics-api` 조회, 출판의 제목·글 수 조회, `apps/api`의 블로그 대시보드 조합 | 오늘과 지난날을 합친 요약, 추이, 인기 글, 남의 블로그 거부 |
