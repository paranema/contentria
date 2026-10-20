# publishing 컨텍스트 (출판)

블로그, 카테고리, 글의 작성과 발행을 담당한다. 핵심 서브도메인이다.

| 애그리거트 | 멤버 | 공개 범위 |
|---|---|---|
| `Blog` | 없음 | 누구나 |
| `CategoryTree` | `Category` | 누구나 |
| `Post` | `PostSlug` (현재 slug와 옛 slug) | 발행된 글은 누구나, 나머지는 블로그 소유자만 |

**내부 구조: 헥사고날.** 발행 상태, slug, 카테고리 트리, 소유권처럼 규칙이 가장 많은 컨텍스트다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)).
코드 위치는 `backend/contexts/publishing/`(`publishing-api`, `publishing-core`)다. P0~P5를 마쳤다. 아웃박스의 릴레이는 상호작용을 만들 때 붙인다.

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 블로그, 카테고리, 글, slug 규칙 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지 | 구현하는 사람 |

01은 아키텍처를 모른다. 제품이 어떻게 동작하는지만 적는다.
02는 01을 전제로 배치를 적는다. 아키텍처가 바뀌면 02만 바뀐다.

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md) | 블로그, 카테고리, 글을 한 컨텍스트로 묶은 이유. 조회수와 좋아요 수를 출판이 소유하지 않는 이유 |
| [strategic/04-context-map.md](../../strategic/04-context-map.md) | 회원, 미디어, 상호작용, 알림과 주고받는 것. `PostPublished`, `PostDeleted` 이벤트 |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 회원 조회를 원격 호출처럼 다루는 방법, 아웃박스 |

## 레거시

레거시 구현은 태그 `legacy-final`에 있다(`backend/blog-api/.../api/{blog,category,post}`). 참고만 하고, 이 폴더의 규칙을 기준으로 새로 만든다.
레거시와 달라진 점은 01-rules의 "레거시와 다른 점"에 모았다.
