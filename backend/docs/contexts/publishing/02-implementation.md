# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.
규칙이 바뀌면 01을, 구현 방법이 바뀌면 이 문서를 고친다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 헥사고날 규칙을 따른다.
불변식과 정책을 가르는 기준은 회원과 같다([member/02-implementation.md](../member/02-implementation.md)의 "불변식과 정책을 가르는 기준"). 애그리거트 하나만 보고 판단할 수 있으면 도메인, 다른 애그리거트나 다른 컨텍스트를 조회해야 하면 앱이다.

## 애그리거트 경계

| 애그리거트 | 루트 | 멤버 | 식별자 |
|---|---|---|---|
| Blog | `Blog` | 없음 | 블로그 ID |
| CategoryTree | `CategoryTree` | `Category` | 블로그 ID와 같은 값. 블로그마다 하나 |
| Post | `Post` | `PostSlug` | 글 ID |

**카테고리는 하나하나가 아니라 블로그의 트리 전체가 애그리거트다.**
깊이 2단계, 같은 부모 아래 이름 중복 금지, 하위가 남은 카테고리 삭제 금지는 모두 트리 전체를 봐야 판단할 수 있다.
편집 화면도 트리 전체를 한 번에 저장한다. [strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md)의 애그리거트 `Category`는 이 트리를 말한다.

**옛 slug는 글의 멤버다.** 옛 slug는 그 글로 가는 리디렉션이고, 글이 지워지면 함께 지워진다.
현재 slug와 옛 slug를 같은 테이블(`post_slugs`)에 두고 `(blog_id, slug)`에 고유 제약을 건다. 블로그 안에서 현재와 옛 slug를 합쳐 겹치지 않는다는 규칙을 DB가 보증한다.

**글은 블로그 소유자를 저장하지 않는다.** 블로그 ID만 들고, 소유 확인은 블로그를 읽어서 한다.
1인 블로그라서 작성자는 언제나 블로그 소유자다. 글에 소유자를 복사하면 두 값이 어긋날 수 있다.

### 한 트랜잭션에 여러 애그리거트를 만드는 경우

블로그를 만들 때 Blog, CategoryTree, 샘플 Post 두 개를 한 트랜잭션에서 만든다.
회원 가입과 같은 이유로 허용한다. "한 트랜잭션에는 한 애그리거트"는 여러 애그리거트를 동시에 **수정**할 때의 경합을 피하려는 규칙이고, 새로 만드는 애그리거트에는 경합할 다른 트랜잭션이 없다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `blogs.slug` UNIQUE | 블로그 slug는 플랫폼 전체에서 고유 |
| `categories(blog_id, slug)` UNIQUE | 카테고리 slug는 블로그 안에서 고유 |
| `post_slugs(blog_id, slug)` UNIQUE | 글의 현재 slug와 옛 slug는 블로그 안에서 모두 합쳐 고유 |
| `post_slugs(post_id)` 중 현재 slug 하나 | 글마다 현재 slug는 하나 (부분 고유 인덱스 `WHERE current`) |

외래키는 애그리거트 안에만 둔다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "외래키").

| 외래키 | 있음 |
|---|---|
| `categories.tree_id → category_trees.id` | 있음. 같은 애그리거트 |
| `post_slugs.post_id → posts.id` | 있음. 같은 애그리거트 |
| `posts.blog_id → blogs.id`, `posts.category_id → categories.id`, `category_trees.id → blogs.id` | 없음. 애그리거트 사이 |

### 앱 확인과 DB 제약이 함께 필요한 규칙

| 규칙 | 앱이 하는 일 | 동시 요청에서 |
|---|---|---|
| 블로그 slug 중복 | 저장 전에 조회해 알아듣기 쉬운 오류를 준다 | 고유 제약이 막는다 |
| 회원당 블로그 3개 | 저장 전에 그 회원의 블로그 수를 센다 | 막지 못한다. 동시에 두 개를 만들면 4개가 될 수 있다. 받아들인다 |
| 글 slug 접미사(`-2`, `-3`) | 블로그의 같은 접두어 slug를 조회해 다음 번호를 고른다 | 고유 제약이 둘째를 막는다. 그 요청은 실패하고 다시 시도하면 된다 |
| 글이 있는 카테고리 삭제 금지 | 지우기 전에 그 카테고리의 글이 있는지 조회한다 | 막지 못한다. 지우는 사이에 그 카테고리로 저장된 글은 없는 카테고리를 가리킨다. 읽을 때 분류 없음으로 다룬다 |

## 이벤트

| 이벤트 | 언제 | 소비 | 담는 것 |
|---|---|---|---|
| `PostPublished` | 글을 처음 발행할 때. 다시 공개할 때는 아니다 | 알림 | 글 ID, 블로그 ID, 블로그 소유자 ID, 발행 시각 |
| `PostDeleted` | 글을 지울 때 | 상호작용 | 글 ID, 블로그 ID |

이벤트는 상태를 바꾸는 트랜잭션 안에서 `publishing` 스키마의 아웃박스 테이블에 기록한다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)).
트랜잭션이 롤백되면 이벤트도 남지 않는다. 아웃박스에서 Kafka로 보내는 릴레이는 첫 소비자(상호작용)를 만들 때 함께 만든다.

`PostPublished`가 블로그 소유자 ID를 담는 이유: 알림이 "누구의 블로그인가"를 출판에 다시 묻지 않게 한다. 출판은 발행할 때 이미 블로그를 읽었다.

## 다른 컨텍스트 호출

| 호출 | 위치 | 방식 |
|---|---|---|
| 회원: 계정이 ACTIVE인가 | 출판의 포트 `BloggerStatusPort.canPublish(bloggerId)`를 번역 계층 `adapter/required/member`가 구현한다. 그 안에서만 `member-api`의 `GetAccountStatusQuery`를 부른다 | 쓰기 트랜잭션을 열기 **전에** 조회한다. 회원 조회가 원격 호출이 되어도 트랜잭션이 그동안 DB 연결을 붙잡지 않는다 |

이름과 구조는 [architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "예시: 출판이 계정 상태를 확인한다"를 따른다.
회원은 아직 `GetAccountStatusQuery`를 공개하지 않는다. P3에서 `member-api`에 추가한다.
미디어 연결은 같은 문서의 "예: 글 저장과 첨부 승격"을 미디어를 만들 때 확정한다.

## 규칙과 도메인 메서드

| 01의 규칙 | 메서드 | 비고 |
|---|---|---|
| 블로그 slug 형식, 변경 불가 | `Blog.create(ownerId, slug, title, description)` | slug에 setter가 없다 |
| 블로그 제목, 설명 수정 | `Blog.changeSettings(title, description)` | 소유 확인은 유스케이스 |
| 카테고리 깊이, 이름 중복, 하위가 남은 삭제 | `CategoryTree.replaceWith(edit)` | 트리 전체를 받아 검증하고 바꾼다. 글이 있는지는 유스케이스가 먼저 확인한다 |
| 카테고리 slug | `CategoryTree` 안에서 이름으로 만든다 | 트리 안에서 겹치면 접미사 |
| 새 글은 DRAFT | `Post.draft(blogId, title, body, summary, categoryId, slug)` | slug는 유스케이스가 고유하게 정해 넘긴다 |
| 발행, 비공개, 다시 공개 | `Post.publish(now)`, `Post.hide()`, `Post.republish()` | 처음 발행인지 돌려준다. 유스케이스가 그때만 `PostPublished`를 기록한다 |
| 발행 전 slug는 제목을 따라감 | `Post.revise(title, body, summary, categoryId, slugIfDraft)` | 발행 전에만 새 slug를 받는다 |
| 발행 후 slug 바꾸기, 옛 slug, 되돌리기 | `Post.changeSlug(newSlug)` | 현재 slug를 옛 slug로 내리고, 자기 옛 slug면 그것을 현재로 올린다 |
| slug 만들기 | `Slugs.fromTitle(title)` | 순수 함수. 접미사는 고르지 않는다 |
| 요약 | `Summaries.from(markdown)` | 마크다운 문법을 걷어 낸 앞 150자 |

## 단계

member 이전과 같이 단계마다 PR을 하나씩 낸다.

| 단계 | 내용 | 완료 조건 |
|---|---|---|
| P0 | 이 문서와 01-rules, README | 결정 항목 확정 |
| P1 | 모듈 골격(`publishing-api`, `publishing-core`), ArchUnit 규칙, 도메인 | 도메인 규칙마다 테스트 |
| P2 | `publishing` 스키마, Flyway V1, 마이그레이터에 컨텍스트 추가, 매핑 테스트 | 빈 DB에 적용, 스키마를 넘는 FK 검사 통과 |
| P3 | `member-api`의 계정 상태 조회와 출판의 번역 계층 | 정지된 계정의 쓰기 거부 |
| P4 | 유스케이스와 웹. 블로그, 카테고리, 글, 공개 조회, 옛 slug 리디렉션 | 01-rules 흐름별 HTTP 테스트 |
| P5 | 아웃박스 기록 | 롤백되면 이벤트도 남지 않는 테스트 |

## 아직 없는 것

| 항목 | 상태 |
|---|---|
| 코드 | P1부터 |
| 아웃박스 릴레이와 Kafka | 상호작용을 만들 때 |
| 미디어 연결(대표 이미지, 본문 이미지) | 미디어를 만들 때 |
| 글 목록 정렬과 페이지 크기 | P4에서 정한다 |
