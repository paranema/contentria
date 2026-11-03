# 분석 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 방문 수집, 일별 통계 집계, 대시보드·조회수 조회 규칙을 실제 코드와 데이터베이스에 어떻게 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

분석 컨텍스트(`contexts/analytics`)는 단건 방문 기록 추가(`INSERT`)를 제외한 대부분의 작업이 SQL 집계 쿼리 중심이므로 [architecture/02-context-architecture.md](../../architecture/02-context-architecture.md)의 **레이어드 아키텍처**(`presentation → application → domain ← infra`)를 따릅니다. 단일 애그리거트만으로 판단할 수 있는 값 형식 검증과 정규화는 도메인(`Visit`)에서 처리하고, 출판 컨텍스트 조회나 기존 기록 조회가 필요한 규칙은 응용 계층에서 조율합니다.

### 스프링 설정 구성

실시간 방문 수집과 통계 조회는 API 서버(`apps/api`)에서, 일별 통계 집계와 90일 경과 원본 삭제는 배치 서버(`apps/batch`)에서 실행됩니다. 두 프로세스가 각자 필요한 빈만 로드하도록 스프링 설정을 세 클래스로 분리합니다.

| 설정 클래스                   | 등록 대상 컴포넌트                                                                     | 로드 프로세스            |
| ----------------------------- | -------------------------------------------------------------------------------------- | ------------------------ |
| `AnalyticsCoreConfiguration`  | 엔티티, 리포지토리, 통계 조회·집계(`application/statistics`, `infra/persistence`)      | `apps/api`, `apps/batch` |
| `AnalyticsWebConfiguration`   | 실시간 방문 수집(`presentation/web`, `application/visit`, `PublishingClient`, 비밀 키) | `apps/api`               |
| `AnalyticsBatchConfiguration` | 일별 통계 집계 배치 잡(`presentation/batch/DailyStatisticsJob`)                        | `apps/batch`             |

방문 수집 흐름(`AnalyticsWebConfiguration`)만 출판 컨텍스트와 방문자 키 비밀 키(`ANALYTICS_VISITOR_KEY_SECRET`)를 참조하므로, `apps/batch`는 출판 모듈이나 비밀 키 환경 변수 없이 DB 연결만으로 기동됩니다.

## 데이터 모델

### 애그리거트 구성

분석 컨텍스트는 개별 방문 사건을 적재하는 `Visit`과 일 단위로 사전 집계된 수치를 보관하는 `DailyStatistics` 두 모델로 구성됩니다. 두 테이블 사이에는 외래키나 직접적인 참조 관계가 없으며, `DailyStatistics`는 `Visit` 레코드들을 집계한 파생 결과입니다.

| 모델 (`analytics` 스키마)              | 보유 상태                                                                          | 영속화 및 변경 방식                                                                                                |
| -------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `Visit` (`visits`)                     | 블로그 ID, 게시글 ID(블로그 홈 방문이면 `NULL`), 방문자 키, 유입 도메인, 방문 시각 | JPA 엔티티로 단건 저장(`INSERT`)하며 수정되지 않습니다. 90일이 지나면 배치에서 삭제합니다.                         |
| `DailyStatistics` (`daily_statistics`) | 블로그 ID, 게시글 ID(블로그 전체 통계면 `NULL`), 집계 날짜, 방문자 수, 조회수      | 한 번에 수천 행을 일괄 갱신(`UPSERT`)하므로 JPA 엔티티를 거치지 않고 `DailyStatisticsSql`의 SQL로 직접 기록합니다. |

### 데이터베이스 스키마

타 컨텍스트의 블로그·게시글은 ID로만 참조하므로 물리적 외래키(FK)를 두지 않으며, 도메인 이벤트를 발행하거나 구독하지 않으므로 `outbox_event`·`inbox_event` 테이블도 두지 않습니다. 스키마 소유권은 `analytics_migrator` 롤에 있고 `api_app`(방문 기록·조회)과 `batch_app`(일별 집계·원본 삭제) 롤은 DML 권한만 가집니다. 데이터 무결성과 집계 성능을 보장하기 위해 설정한 제약 조건과 인덱스는 다음과 같습니다.

| 테이블             | 제약 조건 및 인덱스                                       | 보장하는 규칙 및 조회 용도                                                                         |
| ------------------ | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `visits`           | `CHECK (visitor_key ~ '^[0-9a-f]{64}$')`                  | 해시되지 않은 IP 원문이 실수로 저장되는 것을 DB 수준에서 한 번 더 차단                             |
| `visits`           | `(blog_id, post_id, visitor_key, visited_at)` 인덱스      | 최근 30분 이내 동일 방문 존재 여부 확인                                                            |
| `visits`           | `(blog_id, visited_at)` 인덱스                            | 특정 블로그의 당일 실시간 방문자 수·조회수 합산                                                    |
| `visits`           | `(visited_at)` 인덱스                                     | 일별 통계 배치 집계 및 90일 경과 원본 기록 삭제                                                    |
| `daily_statistics` | `UNIQUE NULLS NOT DISTINCT (blog_id, post_id, stat_date)` | 날짜·블로그·게시글당 1행 보장 (`post_id IS NULL`인 블로그 전체 행도 중복 생성되지 않고 `UPSERT`됨) |
| `daily_statistics` | `CHECK (visitors >= 0 AND views >= visitors)`             | 음수 통계 방지 및 조회수가 고유 방문자 수 이상임을 보장                                            |
| `daily_statistics` | `(blog_id, stat_date)` 인덱스                             | 블로그 대시보드 추이, 누적 합계, 기간 내 인기 글 조회                                              |
| `daily_statistics` | `(post_id) WHERE post_id IS NOT NULL` 부분 인덱스         | 개별 게시글의 누적 조회수 합산                                                                     |

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 **방문 기록 수집(흐름 1) → 일별 통계 집계(흐름 2) → 통계 조회(흐름 3·4)** 순서에 따른 계층별 구현입니다.

| 흐름                   | 진입점                               | 응용 계층                      | 도메인 및 인프라 계층                                  |
| ---------------------- | ------------------------------------ | ------------------------------ | ------------------------------------------------------ |
| 흐름 1. 방문 기록 수집 | `VisitController` (`apps/api`)       | `VisitRecorder`, `VisitorKeys` | `PublishingClient` → `Visit.record`, `VisitRepository` |
| 흐름 2. 일별 통계 집계 | `DailyStatisticsJob` (`apps/batch`)  | `DailyStatisticsService`       | `DailyStatisticsSql`                                   |
| 흐름 3·4. 통계 조회    | `analytics-api` 공개 쿼리 인터페이스 | `TrafficQueryService`          | `TrafficSql`                                           |

### 흐름 1. 방문 기록 수집

프론트엔드가 블로그 홈이나 게시글 상세 화면을 열 때 호출하는 `POST /analytics/visits` 요청은 다음 네 단계로 처리됩니다.

1. **요청 수신 및 클라이언트 IP 추출 (`VisitController`)**: 비로그인 독자도 호출할 수 있으며, 유효 방문으로 기록되든 조건에 걸려 무시되든 항상 `204 No Content`를 반환합니다. 프록시(Next.js 서버 등) 뒤에서 실제 독자 IP를 얻기 위해 `apps/api`에 `server.forward-headers-strategy: native`를 설정하며, 사설 네트워크 대역에서 전달된 `X-Forwarded-For` 헤더를 Tomcat이 검증해 `request.remoteAddr`를 갱신하므로 컨트롤러는 헤더를 직접 파싱하지 않고 표준 `remoteAddr`만 읽습니다.
2. **출판 컨텍스트 사전 조회 (`PublishingClient`)**: `VisitRecorder`는 **쓰기 트랜잭션을 열기 전에** `infra/client/PublishingClient`(빈 이름 `analyticsPublishingClient`)를 통해 `publishing-api`를 호출하여 대상 블로그·게시글의 유효성과 소유자를 확인합니다. 블로그가 존재하지 않거나(1-1), 게시글이 해당 블로그의 공개(`PUBLISHED`) 글이 아니거나(1-2), 로그인한 방문자가 블로그 소유자 본인이면(1-3) 즉시 종료합니다. 향후 서비스 분리 시 출판 서비스가 일시적으로 응답하지 않으면 잘못된 방문을 통계에 섞는 것보다 방문 1건을 유실하는 편이 안전하므로 조용히 버립니다.
3. **방문자 키 계산 및 30분 중복 확인 (`VisitorKeys`, `VisitRecorder`)**: `VisitorKeys`는 `HMAC-SHA256(비밀 키, 날짜 + "\u0000" + IP + "\u0000" + User-Agent)`로 64자 소문자 16진수 해시를 계산합니다. 필드 사이에 `NUL(\u0000)` 구분자를 넣어 인접 문자열이 교묘히 겹쳐 동일 해시가 되는 충돌을 막으며, 비밀 키는 최소 32자 이상이어야 합니다(기본값 없음). 트랜잭션을 시작한 뒤 최근 30분 이내에 동일 `(blogId, postId, visitorKey)` 기록이 있는지 조회하고(1-4), 없을 때만 저장합니다. 동일 방문자의 요청이 동시에 두 번 도달하면 둘 다 통과할 수 있으나, 조회수는 근사 지표이므로 별도의 잠금을 걸지 않습니다.
4. **도메인 불변식 검증 및 정규화 (`Visit.record`)**: `Visit.record(blogId, postId, visitorKey, referrer, at)`는 전달받은 `visitorKey`가 64자 소문자 16진수 형식인지 검증하고, `referrer` URL에서 `http`/`https` 스킴의 소문자 호스트명만 추출해 엔티티를 생성합니다(경로·쿼리스트링·포트·계정 정보나 비정상 URI는 모두 버림).

| `PublishingClient` 확인 항목                  | 호출하는 `publishing-api` 인터페이스 |
| --------------------------------------------- | ------------------------------------ |
| 블로그 존재 여부 및 소유자 ID 확인            | `GetBlogReferencesQuery.getBlogs`    |
| 게시글 존재 여부, 소속 블로그, 발행 상태 확인 | `GetPostReferencesQuery.getPosts`    |

### 흐름 2. 일별 통계 집계

배치 서버(`apps/batch`)의 `analyticsDailyStatistics` 잡(`DailyStatisticsJob` → `DailyStatisticsService`)은 매일 실행될 때마다 **최근 3일 치(어제·그제·3일 전)** 방문 기록을 날짜별 독립 트랜잭션으로 재집계한 뒤, 마지막 트랜잭션에서 90일이 지난 `visits` 레코드를 일괄 삭제합니다. `apps/batch`에는 미디어 정리 잡 등 여러 배치 잡이 함께 등록되어 있으므로 실행 시 `--spring.batch.job.name=analyticsDailyStatistics`(또는 `BATCH_JOB_NAME` 환경 변수)를 지정합니다.

집계 쿼리인 `DailyStatisticsSql.aggregate`는 `GROUPING SETS ((blog_id, post_id), (blog_id))`를 사용해 게시글별 수치와 블로그 전체(`post_id IS NULL`) 수치를 한 번에 구하고 `ON CONFLICT ON CONSTRAINT uq_daily_statistics_blog_post_date DO UPDATE`로 덮어씁니다. 이때 `HAVING GROUPING(post_id) = 1 OR post_id IS NOT NULL` 조건을 반드시 함께 지정합니다. 이 조건이 없으면 블로그 홈 방문(`post_id IS NULL`)이 만든 그룹과 블로그 전체 집계 그룹이 동일한 키 `(blog_id, NULL, stat_date)`를 생성하여, 단일 `UPSERT` 문 안에서 같은 행을 두 번 갱신하려다 오류가 발생하기 때문입니다.

### 흐름 3·4. 통계 조회

분석 컨텍스트는 블로그 대시보드나 게시글 화면을 위한 자체 조회용 HTTP 컨트롤러를 두지 않고, 화면 조합 계층에서 가져다 쓸 수 있도록 `analytics-api` 모듈에 읽기 전용 쿼리 인터페이스만 공개합니다. 구현체인 `TrafficQueryService`(`TrafficSql` 호출)는 **오늘 수치는 `visits` 테이블에서 실시간으로 세고, 어제 이전 수치는 `daily_statistics` 테이블에서 읽어** 합산합니다.

| `analytics-api` 공개 인터페이스                         | 반환 데이터                                                    | 대응 규칙 및 사용처                     |
| ------------------------------------------------------- | -------------------------------------------------------------- | --------------------------------------- |
| `GetBlogTrafficQuery.summary(blogId)`                   | 오늘·어제·누적 방문자 수와 조회수                              | 흐름 3 — 블로그 대시보드 요약 지표      |
| `GetBlogTrafficQuery.trend(blogId, days)`               | 최근 `days`일간의 일별 방문자 수와 조회수 (방문 없는 날은 `0`) | 흐름 3 — 블로그 대시보드 일별 방문 추이 |
| `GetBlogTrafficQuery.popularPosts(blogId, days, limit)` | 기간 내 조회수 상위 게시글 ID 및 조회수 목록                   | 흐름 3 — 블로그 대시보드 인기 글 선정   |
| `GetViewCountsQuery.viewCounts(postIds)`                | 요청한 게시글 ID별 누적 조회수 (기록이 없는 ID는 `0` 반환)     | 흐름 4 — 게시글 목록·상세 화면 조회수   |

#### 블로그 대시보드 조회 조합 (`apps/api`)

[01-rules.md](01-rules.md)의 흐름 3(블로그 대시보드 통계 조회)은 제품 요구사항상 "블로그 소유권 확인"과 "인기 글의 제목·슬러그 및 총 발행 글 수 표시"를 포함합니다. 그러나 분석 컨텍스트는 블로그 ID와 게시글 ID만 알고 있을 뿐 누가 소유자인지, 게시글 제목이 무엇인지는 알지 못합니다.

따라서 실제 대시보드 엔드포인트(`GET /me/blogs/{slug}/dashboard?range=2weeks`)는 분석 컨텍스트 내부가 아니라 **`apps/api`의 조회 조합 계층**(`composition/dashboard/BlogDashboard`, `BlogDashboardController`)에 두며, 출판 컨텍스트(`publishing-api`)와 분석 컨텍스트(`analytics-api`)를 차례로 호출해 단일 응답으로 조립합니다([architecture/03-context-integration.md](../../architecture/03-context-integration.md) 참고).

1. **소유권 확인 (`publishing-api`)**: `GetBlogReferencesQuery.getBlogBySlug`로 블로그 정보와 소유자 ID를 조회해 현재 요청자와 일치하는지 검증합니다(존재하지 않거나 타인의 블로그면 `404 BLOG_NOT_FOUND`).
2. **트래픽 지표 조회 (`analytics-api`)**: `GetBlogTrafficQuery`를 호출해 요약 수치(`summary`), 선택한 기간(`2weeks`·`30days`·`90days`)의 일별 추이(`trend`), 최근 30일 인기 글 ID 목록(`popularPosts`)을 가져옵니다.
3. **게시글 정보 결합 (`publishing-api`)**: `GetPostOverviewsQuery`로 인기 글들의 제목·슬러그(`getTitles`)와 총 발행 게시글 수(`countPublished`)를 조회해 합칩니다. 이때 집계 이후 삭제된 게시글이 빠져 인기 글이 5개 미만이 되는 것을 방지하기 위해, 분석 컨텍스트에는 표시 개수(5개)의 2배수인 10개를 요청한 뒤 현재 출판 컨텍스트에 존재하는 글만 최대 5개까지 추려 반환합니다.
