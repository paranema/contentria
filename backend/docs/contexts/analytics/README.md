# analytics 컨텍스트 (분석)

독자의 방문을 기록하고, 조회수와 방문자 수를 세고, 하루 단위로 집계한다. 블로그 대시보드에 숫자를 공급한다. 지원 서브도메인이다.

| 애그리거트 | 멤버 | 공개 범위 |
|---|---|---|
| `Visit` (방문 한 건) | 없음 | 아무도 원본을 보지 않는다. 숫자로만 쓴다 |
| `DailyStatistics` (하루 집계) | 없음 | 블로그 소유자. 글의 조회수는 누구나 |

**내부 구조: 레이어드.** 쓰기는 기록 추가뿐이고 나머지는 집계 쿼리다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)).
코드 위치는 `backend/contexts/analytics/`(`analytics-api`, `analytics-core`)다. 지금은 모듈 골격, 아키텍처 규칙, 방문 도메인이 있다(A1).

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 무엇을 방문으로 세는지, 집계, 대시보드의 숫자, 보관 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지, 출판 조회, 배치, 조회 조합, 단계 | 구현하는 사람 |

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md) | 조회수를 분석이 소유하는 이유 |
| [strategic/04-context-map.md](../../strategic/04-context-map.md) | 분석이 출판에 묻는 것. 블로그 대시보드의 조회 조합 |
| [publishing/01-rules.md](../publishing/01-rules.md) | 블로그 소유자, 발행된 글. 방문으로 셀 대상의 근거 |

## 레거시

레거시 구현은 태그 `legacy-final`에 있다. 참고만 하고, 이 폴더의 규칙을 기준으로 새로 만든다.

| 기능 | 레거시 위치 |
|---|---|
| 방문 기록, 30분 중복 제외, 소유자 방문 제외 | `backend/blog-api/.../api/analytics/` |
| 방문 기록과 일별 통계 엔티티, 집계 쿼리 | `backend/blog-common/.../common/domain/analytics/` |
| 일별 집계 잡 | `backend/blog-batch/.../jobs/statistics/DailyStatisticsJobConfig.kt` |
| 블로그 대시보드 (요약, 방문 추이, 인기 글) | `backend/blog-api/.../api/dashboard/` |

레거시와 달라진 점은 01-rules의 "레거시와 다른 점"에 모았다.
