# 분석 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 각 규칙을 어느 계층과 컴포넌트에 배치해 구현했는지 기록합니다.

- 내부 계층 구조는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **레이어드 아키텍처** 규칙을 따릅니다.
- 단일 애그리거트만으로 판단할 수 있으면 도메인, 기존 기록이나 외부 컨텍스트를 조회해야 하면 응용 계층에서 처리합니다.
  - 분석 컨텍스트는 대부분의 규칙이 기존 방문 기록이나 출판 컨텍스트 조회를 수반하므로 응용 계층 비중이 높습니다.

## 애그리거트 경계 및 도메인 모델

분석 컨텍스트는 개별 방문 사건을 기록하는 `Visit`과 일 단위로 사전 집계된 통계를 보관하는 `DailyStatistics` 두 애그리거트로 구성됩니다. 두 애그리거트 사이에는 직접적인 참조 관계가 없으며, `DailyStatistics`는 `Visit` 레코드들을 집계한 파생 결과입니다.

| 애그리거트        | 보유 상태                                                                          | 생명주기 및 변경 방식                                     |
| ----------------- | ---------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `Visit`           | 블로그 ID, 게시글 ID(블로그 홈 방문이면 `NULL`), 방문자 키, 유입 도메인, 방문 시각 | 생성 후 수정되지 않으며 90일 경과 시 배치에서 삭제합니다. |
| `DailyStatistics` | 블로그 ID, 게시글 ID(블로그 전체 통계면 `NULL`), 집계 날짜, 방문자 수, 조회수      | 일별 집계 배치가 해당 날짜의 집계 값을 통째로 갱신합니다. |

- `DailyStatistics`는 한 번의 배치 실행에서 수천 행을 일괄 갱신(`UPSERT`)하므로 JPA 엔티티로 건별 로드하지 않고 SQL 집계 쿼리(`DailyStatisticsSql`)로 직접 기록합니다.

`Visit` 도메인 엔티티는 생성 시점에 개인정보 보호와 데이터 정합성을 위해 다음과 같은 값 검증 및 정규화 불변식을 보장합니다.

| 도메인 메서드 및 값 규칙                                 | 구현 상세                                                                                                       |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `Visit.record(blogId, postId, visitorKey, referrer, at)` | 방문자 키가 소문자 16진수 64자(`HMAC-SHA256` 해시 형식)인지 검증하여 IP 원문이 실수로 저장되는 것을 차단합니다. |
| 유입 도메인(`referrer`) 정규화                           | `http`/`https` URL에서 소문자 호스트명만 추출하며 경로·쿼리·포트·계정 정보나 비정상 URI는 모두 버립니다.        |

## 도메인 규칙의 계층별 배치

[01-rules.md](01-rules.md)에 정의된 각 비즈니스 흐름과 규칙을 담당하는 계층 및 주요 클래스 매핑은 다음과 같습니다.

| `01-rules.md`의 규칙                         | 담당 위치                                              | 구현 방식                                                                                            |
| -------------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| 흐름 1-1~3 (블로그·글 유효성 및 소유자 제외) | 앱 (`VisitRecorder`) + `infra/client/PublishingClient` | 트랜잭션 시작 전 출판 컨텍스트에 블로그 소유자와 게시글 공개 여부를 조회합니다.                      |
| 흐름 1-4 (30분 이내 중복 방문 제외)          | 앱 (`VisitRecorder`)                                   | 동일 방문자 키·블로그·게시글의 최근 30분 내 기록 존재 여부를 조회합니다.                             |
| 흐름 1-5 (방문 기록 생성 및 도메인 추출)     | 도메인 (`Visit.record`)                                | 방문자 키 형식 검증 및 유입 도메인 정규화를 수행합니다.                                              |
| 방문자 키 생성 (`VisitorKeys`)               | 앱 (`application/VisitorKeys`)                         | `HMAC-SHA256(비밀 키, 날짜 + NUL + IP + NUL + User-Agent)`로 계산합니다.                             |
| 흐름 2 (일별 통계 집계 및 90일 경과 삭제)    | 앱 (`DailyStatisticsService`) + 배치 어댑터            | `GROUPING SETS`로 블로그 전체와 게시글별 수치를 한 번에 계산해 `ON CONFLICT DO UPDATE`로 저장합니다. |
| 흐름 3, 4 (대시보드 및 게시글 조회수 조회)   | 앱 (`TrafficQueryService`) + `TrafficSql`              | 오늘 수치는 `visits`에서, 어제 이전 수치는 `daily_statistics`에서 읽어 합산합니다.                   |

- 동일한 방문자의 요청이 동시에 두 번 도달하면 두 요청 모두 30분 중복 검사를 통과해 2건으로 기록될 수 있습니다. 조회수는 근사 지표이므로 이를 막기 위해 별도의 잠금이나 고유 제약을 걸지 않습니다.

## 컨텍스트 간 연동 인터페이스

분석 컨텍스트는 방문 유효성을 검증하기 위해 출판 컨텍스트를 단방향으로 호출하며, 집계된 통계 지표를 외부에서 조합할 수 있도록 `analytics-api` 모듈을 통해 조회 인터페이스를 공개합니다.

### 출판 컨텍스트 조회 (`PublishingClient`)

방문 기록을 저장하기 전에 대상 블로그와 게시글이 유효한지 확인하기 위해 `infra/client/PublishingClient`에서 `publishing-api`의 쿼리를 호출합니다.

| 호출 목적                                     | 사용하는 `publishing-api` 인터페이스 |
| --------------------------------------------- | ------------------------------------ |
| 블로그 존재 여부 및 소유자 ID 확인            | `GetBlogReferencesQuery.getBlogs`    |
| 게시글 존재 여부, 소속 블로그, 발행 상태 확인 | `GetPostReferencesQuery.getPosts`    |

- 향후 서비스 분리 시 출판 서비스가 일시적으로 응답하지 않으면 해당 방문 기록은 조용히 버립니다(잘못된 방문이나 소유자 방문을 집계에 섞는 것보다 방문 1건을 유실하는 편이 안전하기 때문입니다).
- 상호작용 컨텍스트에도 동일한 이름의 `PublishingClient` 클래스가 존재하므로, 단일 프로세스(`apps/api`) 내 스프링 빈 이름 충돌을 피하기 위해 빈 이름을 `analyticsPublishingClient`로 명시합니다.

### 외부에 공개하는 통계 쿼리 (`analytics-api`)

블로그 대시보드나 게시글 화면 조합 계층에서 통계 데이터를 가져갈 수 있도록 `analytics-api`에 다음 읽기 전용 쿼리(`TrafficQueryService` 구현)를 공개합니다.

| 공개 인터페이스                                         | 제공 데이터                                                    | 호출 주체                       |
| ------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------- |
| `GetBlogTrafficQuery.summary(blogId)`                   | 오늘·어제·누적 방문자 수와 조회수                              | `apps/api` 블로그 대시보드 조합 |
| `GetBlogTrafficQuery.trend(blogId, days)`               | 최근 `days`일간의 일별 방문자 수와 조회수 (방문 없는 날은 `0`) | `apps/api` 블로그 대시보드 조합 |
| `GetBlogTrafficQuery.popularPosts(blogId, days, limit)` | 기간 내 조회수 상위 게시글 ID 및 조회수 목록                   | `apps/api` 블로그 대시보드 조합 |
| `GetViewCountsQuery.viewCounts(postIds)`                | 요청한 게시글 ID별 누적 조회수 (기록이 없는 ID는 `0` 반환)     | 게시글 목록·상세 화면 조합      |

- 분석 컨텍스트는 블로그 ID와 게시글 ID만 알고 있으므로, 대시보드 요청자가 해당 블로그의 소유자인지 검증하는 책임은 대시보드를 조합하는 진입점(`apps/api`)에 있습니다.

## 블로그 대시보드 조회 조합

블로그 대시보드 API(`GET /me/blogs/{slug}/dashboard?range=2weeks`)는 `apps/api`의 조회 조합 계층(`composition/dashboard/BlogDashboard`, `BlogDashboardController`)에서 출판 컨텍스트와 분석 컨텍스트의 데이터를 모아 단일 응답으로 구성합니다([architecture/03-context-integration.md](../../architecture/03-context-integration.md) 참고).

1. **소유권 확인**: 출판 컨텍스트에서 블로그 정보와 소유자 ID를 조회해 현재 요청자와 일치하는지 확인합니다(불일치하거나 없는 블로그면 `404 BLOG_NOT_FOUND`).
2. **트래픽 지표 조회**: 분석 컨텍스트(`GetBlogTrafficQuery`)에서 요약 수치, 기간별 추이(`2weeks`, `30days`, `90days`), 인기 글 ID 목록을 조회합니다.
3. **게시글 정보 결합**: 출판 컨텍스트(`GetPostOverviewsQuery`)에서 인기 글들의 제목·슬러그와 총 발행 게시글 수를 조회해 결합합니다(그사이 삭제된 게시글이 빠져 5개 미만이 되는 것을 방지하기 위해 분석 컨텍스트에는 표시 개수의 2배수를 요청한 뒤 출판에 존재하는 글만 최대 5개까지 남깁니다).

## 실시간 방문 수집 API 및 방문자 식별

프론트엔드가 블로그 홈이나 게시글 상세 화면 열람 시 호출하는 방문 기록 수집 엔드포인트와 방문자 식별 처리 방식입니다.

| HTTP 요청                                                 | 응답                                                       |
| --------------------------------------------------------- | ---------------------------------------------------------- |
| `POST /analytics/visits` (`{blogId, postId?, referrer?}`) | 항상 `204 No Content` (비로그인 허용, 제외된 방문도 `204`) |

- **클라이언트 IP 추출과 프록시 헤더 처리**: 프론트엔드(Next.js 서버)나 리버스 프록시를 거쳐 전달되는 실제 독자 IP를 올바르게 인식하기 위해 `apps/api`에 `server.forward-headers-strategy: native`를 설정합니다. Tomcat은 사설 네트워크 대역(게이트웨이, Next.js 서버)에서 온 요청에 한해 `X-Forwarded-For` 헤더를 신뢰하여 `request.remoteAddr`를 갱신하며, 분석 컨텍스트는 헤더를 직접 파싱하지 않고 표준 `remoteAddr`만 읽습니다.
- **방문자 키 경계 구분**: `VisitorKeys`는 날짜, IP, `User-Agent` 문자열을 이어 붙일 때 사이에 `NUL(\u0000)` 구분자를 삽입하여 인접 필드 문자열이 교묘하게 겹쳐 동일 해시가 만들어지는 충돌을 방지합니다. 비밀 키(`ANALYTICS_VISITOR_KEY_SECRET`)는 최소 32자 이상이어야 하며 기본값을 두지 않습니다.

## 데이터베이스 스키마 및 제약 조건

`analytics` 스키마의 테이블 인덱스 구성과 데이터 무결성을 보장하기 위한 DB 제약 조건입니다.

| 제약 조건 및 인덱스                                                       | 보장하는 도메인 규칙 및 용도                                                                       |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `visits(blog_id, post_id, visitor_key, visited_at)` 인덱스                | 최근 30분 이내 동일 방문 존재 여부 확인                                                            |
| `visits(visited_at)` 인덱스                                               | 일별 집계, 당일 실시간 수치 계산, 90일 경과 기록 삭제                                              |
| `daily_statistics(blog_id, post_id, stat_date)` UNIQUE NULLS NOT DISTINCT | 날짜·블로그·게시글당 1행 보장 (`post_id IS NULL`인 블로그 전체 행도 중복 생성되지 않고 `UPSERT`됨) |
| `daily_statistics(blog_id, stat_date)` 인덱스                             | 블로그 대시보드 추이 및 누적 합계 조회                                                             |
| 방문자 키 형식 CHECK (`^[0-9a-f]{64}$`) 및 수치 `>= 0` CHECK              | 해시되지 않은 원문 저장 차단 및 음수 통계 방지                                                     |

- 물리적 외래키(FK) 및 `outbox_event`, `inbox_event` 테이블은 두지 않습니다.
- `analytics_migrator` 롤이 스키마를 소유하며, `api_app`(방문 기록·조회)과 `batch_app`(일별 집계·오래된 기록 삭제) 롤은 DML 권한만 가집니다.

## 일별 통계 집계 배치 및 애플리케이션 설정

매일 마감된 방문 기록을 일별 통계로 합산하고 보존 기한이 지난 원본 기록을 정리하는 배치 잡(`apps/batch`) 구성입니다.

| 배치 잡 이름               | 구현 위치                                                         | 동작 방식                                                                                                       |
| -------------------------- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `analyticsDailyStatistics` | `presentation/batch/DailyStatisticsJob`, `DailyStatisticsService` | 최근 3일(어제·그제·3일 전)을 날짜별 트랜잭션으로 재집계(`DailyStatisticsSql`)한 뒤 90일 지난 방문을 삭제합니다. |

- **SQL `GROUPING SETS` 주의점**: `DailyStatisticsSql`은 `GROUPING SETS ((blog_id, post_id), (blog_id))`로 게시글별 수치와 블로그 전체 수치를 한 번에 구하되, `HAVING GROUPING(post_id) = 1 OR post_id IS NOT NULL` 조건을 함께 지정합니다. 만약 이 조건이 없으면 블로그 홈 방문(`post_id IS NULL`) 그룹과 블로그 전체 집계 그룹이 동일한 키 `(blog_id, NULL, stat_date)`를 생성하여 단일 `INSERT ... ON CONFLICT DO UPDATE` 문 안에서 같은 행을 두 번 갱신하려다 오류가 발생하기 때문입니다.

### 프로세스별 스프링 설정 분리

API 서버(`apps/api`)와 배치 서버(`apps/batch`)가 각자 필요한 컴포넌트만 로드할 수 있도록 스프링 설정 클래스를 세 개로 분리합니다.

| 설정 클래스                   | 스캔 및 등록 대상                                                                      | 로드하는 애플리케이션    |
| ----------------------------- | -------------------------------------------------------------------------------------- | ------------------------ |
| `AnalyticsCoreConfiguration`  | 엔티티, 리포지토리, 통계 조회·집계(`application/statistics`, `infra/persistence`)      | `apps/api`, `apps/batch` |
| `AnalyticsWebConfiguration`   | 실시간 방문 기록(`presentation/web`, `application/visit`, `PublishingClient`, 비밀 키) | `apps/api`               |
| `AnalyticsBatchConfiguration` | 일별 집계 배치 잡(`presentation/batch`)                                                | `apps/batch`             |

- 방문 수집 흐름(`AnalyticsWebConfiguration`)만 출판 컨텍스트와 방문자 키 비밀 키를 필요로 하므로, `apps/batch`는 출판 모듈이나 비밀 키 환경 변수 없이 DB 연결만으로 기동됩니다.
- `apps/batch`에는 여러 배치 잡(`mediaImageCleanup`, `mediaVideoCleanup`, `analyticsDailyStatistics`)이 함께 등록되어 있으므로 실행 시 반드시 `--spring.batch.job.name`(또는 `BATCH_JOB_NAME` 환경 변수)을 지정해야 합니다.
