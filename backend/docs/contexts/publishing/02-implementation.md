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
현재 slug는 `posts.slug`에 두고, 그 글이 가진 적 있는 slug는 현재 것까지 모두 `post_slugs`에 둔다. 옛 slug는 `post_slugs`에서 현재 slug를 뺀 것이다.
`post_slugs(blog_id, slug)`에 고유 제약을 걸어, 블로그 안에서 현재와 옛 slug를 합쳐 겹치지 않는다는 규칙을 DB가 보증한다.
"현재" 표시 열은 두지 않는다. 옛 slug로 되돌릴 때 두 행의 표시를 맞바꾸면, 갱신 순서에 따라 잠깐 현재가 둘이 되어 부분 고유 인덱스에 걸린다. 표시가 없으면 `posts.slug` 하나만 바꾸면 된다.

**글은 블로그 소유자를 저장하지 않는다.** 블로그 ID만 들고, 소유 확인은 블로그를 읽어서 한다.
1인 블로그라서 작성자는 언제나 블로그 소유자다. 글에 소유자를 복사하면 두 값이 어긋날 수 있다.

### 한 트랜잭션에 여러 애그리거트를 만드는 경우

블로그를 만들 때 Blog, CategoryTree, 샘플 Post 두 개를 한 트랜잭션에서 만든다.
회원 가입과 같은 이유로 허용한다. "한 트랜잭션에는 한 애그리거트"는 여러 애그리거트를 동시에 **수정**할 때의 경합을 피하려는 규칙이고, 새로 만드는 애그리거트에는 경합할 다른 트랜잭션이 없다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `blogs.slug` UNIQUE | 블로그 slug는 플랫폼 전체에서 고유 |
| `categories(tree_id, slug)` UNIQUE, `DEFERRABLE INITIALLY DEFERRED` | 카테고리 slug는 블로그 안에서 고유. 트리 ID가 블로그 ID다 |
| `categories(tree_id, parent_id, name)`는 두지 않음 | 이름 비교는 대소문자를 가리지 않는다. 트리가 지킨다 |
| `post_slugs(blog_id, slug)` UNIQUE | 글의 현재 slug와 옛 slug는 블로그 안에서 모두 합쳐 고유 |

카테고리 slug 제약과 부모 외래키를 커밋 시점에 검사하는 이유: Hibernate는 한 번의 flush에서 삽입을 삭제보다 먼저 실행하고, 같은 엔티티끼리는 추가된 순서대로 넣는다. 트리를 한 번에 저장하면서 `x`를 지우고 새 `x`를 만들거나, 새 하위를 새 상위보다 먼저 넣으면 즉시 검사하는 제약은 커밋 전에 실패한다. `MigrationMatchesMappingTest`가 두 경우를 검증한다.

외래키는 애그리거트 안에만 둔다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "외래키").

| 외래키 | 있음 |
|---|---|
| `categories.tree_id → category_trees.id` | 있음. 같은 애그리거트 |
| `categories.parent_id → categories.id` | 있음. 같은 애그리거트 |
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
`GetAccountStatusQuery`는 ID 묶음 조회(`getAll`)와 단건 조회(`get`)를 공개하고, 회원의 도메인 상태 대신 공개용 상태(`ACTIVE`, `SUSPENDED`, `CLOSED`)를 돌려준다. 회원이 모르는 ID는 결과에 없고, 출판은 그 계정을 "쓸 수 없음"으로 본다.
회원이 별도 서비스가 되어 응답하지 않으면 그때도 "쓸 수 없음"으로 본다. 거부한 쓰기는 다시 시도하면 되지만, 정지된 블로거의 쓰기를 통과시키면 되돌릴 수 없다.
미디어 연결은 같은 문서의 "예: 글 저장과 첨부 승격"을 미디어를 만들 때 확정한다.

## 규칙과 도메인 메서드

| 01의 규칙 | 메서드 | 비고 |
|---|---|---|
| 블로그 slug 형식, 변경 불가 | `Blog.create(ownerId, slug, title, description)` | slug에 setter가 없다 |
| 블로그 제목, 설명 수정 | `Blog.changeSettings(title, description)` | 소유 확인은 유스케이스 |
| 카테고리 깊이, 이름 중복, 하위가 남은 삭제 | `CategoryTree.replaceWith(edit)` | 트리 전체를 받아 검증하고 바꾼다. 글이 있는지는 유스케이스가 먼저 확인한다 |
| 카테고리 slug | `CategoryTree` 안에서 이름으로 만든다 | 트리 안에서 겹치면 접미사 |
| 새 글은 DRAFT | `Post.draft(blogId, title, body, summary, categoryId, slug)` | slug는 유스케이스가 고유하게 정해 넘긴다 |
| 발행, 비공개, 다시 공개 | `Post.publish(now)`, `Post.hide()`, `Post.republish()` | 각각 DRAFT, PUBLISHED, PRIVATE에서만 된다. `publish`는 언제나 처음 발행이므로 유스케이스는 그때만 `PostPublished`를 기록한다 |
| 발행 전 slug는 제목을 따라감 | `Post.revise(content, categoryId, draftSlug)` | `draftSlug`는 DRAFT일 때만 받는다 |
| 발행 후 slug 바꾸기, 옛 slug, 되돌리기 | `Post.changeSlug(newSlug)` | 새 slug를 `post_slugs`에 더하고 `posts.slug`를 바꾼다. 자기 옛 slug면 더하지 않는다. DRAFT에서는 옛 slug를 남기지 않는다 |
| slug 만들기, 접미사 | `Slugs.from(text, fallback, maxLength)`, `Slugs.nextAvailable(base, taken, maxLength)` | 순수 함수. 무엇이 쓰였는지(`taken`)는 유스케이스가 조회해 넘긴다 |
| 요약 | `PostSummary.written(text)`, `PostSummary.derived(text)` | 도메인은 300자 제한과 작성자가 썼는지만 안다. 본문에서 뽑는 일은 앱이 마크다운 파서로 한다(P4). 레거시도 commonmark로 했다(`post/application/MarkdownService.kt`) |

## 유스케이스

`application/provided`의 유스케이스와 그 구현이다. 하위 단계마다 늘어난다.

| 01의 흐름 | 유스케이스 | 구현 | HTTP |
|---|---|---|---|
| 흐름 1 블로그 만들기 | `CreateBlogUseCase` | `BlogService` | `POST /blogs` → 201 |
| 블로그 설정 | `ChangeBlogSettingsUseCase` | `BlogService` | `PATCH /blogs/{slug}` (소유자) |
| 블로그 조회 | `GetBlogQuery` | `BlogService` | `GET /blogs/{slug}`(공개), `GET /me/blogs` |
| 흐름 2 카테고리 조회 | `GetCategoryTreeQuery` | `CategoryTreeService` | `GET /blogs/{slug}/categories`(공개). 트리와 버전 |
| 흐름 2 카테고리 편집 | `EditCategoryTreeUseCase` | `CategoryTreeService` | `PUT /blogs/{slug}/categories` (소유자). 트리 전체와 편집을 연 때의 버전 |
| 흐름 3 글 쓰기 | `WritePostUseCase` | `PostService` | `POST /blogs/{slug}/posts` → 201, 임시저장 |
| 흐름 3 글 고치기 | `RevisePostUseCase` | `PostService` | `PUT /posts/{id}` |
| 글 slug 바꾸기 | `ChangePostSlugUseCase` | `PostService` | `PUT /posts/{id}/slug`. `slug`가 없으면 제목에서 다시 만든다 |
| 흐름 4 발행, 비공개, 다시 공개 | `ChangePostStatusUseCase` | `PostService` | `POST /posts/{id}/publish`, `/hide`, `/republish` |
| 흐름 5 글 삭제 | `DeletePostUseCase` | `PostService` | `DELETE /posts/{id}` → 204 |
| 소유자의 글 | `GetMyPostsQuery` | `PostService` | `GET /me/posts/{id}`, `GET /me/blogs/{slug}/posts` (임시저장 포함, 최근 수정 순) |

`apps/api`의 필터 체인은 `/blogs/` 아래의 GET을 누구에게나 열고, 나머지는 세션을 요구한다.

### 구현에서 정한 것

| 항목 | 내용 |
|---|---|
| 검사 순서 | 01의 표 순서대로 검사해 처음 걸리는 규칙을 알린다. 블로그 만들기는 계정 상태(트랜잭션 밖), 블로그 수, 형식과 예약어, 중복 순이다. 카테고리 편집은 소유, 트리 규칙(2~4번), 글이 있는 삭제(5번) 순이다 |
| slug 경합 | 같은 주소를 동시에 만들면 고유 제약이 둘째를 막는다. 그 실패는 `BLOG_SLUG_TAKEN`으로 알린다 |
| 트리 버전 | 편집을 연 때의 버전을 받아 지금 버전과 다르면 409 `CATEGORY_TREE_CHANGED`다. 편집용으로 읽을 때 `OPTIMISTIC_FORCE_INCREMENT`로 읽어 저장마다 버전을 올린다. 카테고리는 연관의 역방향이라, 이름만 바뀌거나 카테고리가 더해져도 Hibernate는 트리의 버전을 올리지 않는다. 잠금 방식은 리포지토리가 아니라 `CategoryTree`의 named query에 둔다 |
| 편집 응답 | 커밋한 뒤 트리를 다시 읽어 돌려준다. 버전은 커밋하면서 오르기 때문이다 |
| 요약 | commonmark의 문법 트리에서 읽히는 글자만 모은다. 링크 주소, 이미지, 코드 블록, HTML은 뺀다. 레거시가 쓴 `TextContentRenderer`는 링크를 `"글" (주소)`로, 취소선을 `/글/`로 써서 요약에 섞였다 |
| 샘플 본문 | `publishing-core`의 `samples/*.md`. 제목은 따로 있으므로 본문에 다시 쓰지 않는다 |
| 정지된 계정 | 쓰기 전에 회원에게 묻는다. 액세스 토큰은 정지 뒤에도 만료까지 통과하므로 토큰만 믿지 않는다 |
| 남의 글 | 글 ID로 오는 요청은 소유자가 아니면 상태와 상관없이 404 `POST_NOT_FOUND`다. 남의 임시저장 글이 있다는 사실을 알리지 않는다. 블로그는 공개이므로 남의 블로그에 글을 쓰려 하면 403 `NOT_BLOG_OWNER`다 |
| slug가 쓰였는지 | 그 블로그의 `post_slugs`에서 같은 접두어로 시작하는 slug를 조회한다(`Post`의 named query `findSlugsTakenInBlog`). 그 글 자신의 slug는 빼서 자기 옛 slug로 돌아갈 수 있게 한다. slug에는 LIKE 와일드카드 문자가 올 수 없어 접두어를 그대로 쓴다 |
| 임시저장 글의 slug | 제목이 바뀐 때만 다시 만든다. 본문만 고치면 작성자가 직접 정한 slug가 그대로 남는다 |
| 새 글의 slug | 글의 ID를 알아야 자기 slug를 뺄 수 있으므로, 임시 slug로 만든 뒤 저장 전에 제목의 slug로 바꾼다 |
| 소유자의 글 목록 | 아직 페이지를 나누지 않는다. 공개 목록(P4c)에서 정렬과 페이지 크기를 정할 때 함께 바꾼다 |
| 동시 수정 | 같은 글을 동시에 고치면 나중 것이 남는다. 1인 블로그라 버전을 두지 않았다 |

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
| 공개 조회, 옛 slug 리디렉션 | P4c. P4b까지 블로그, 카테고리, 글 쓰기와 발행, 삭제를 만들었다 |
| 아웃박스 릴레이와 Kafka | 상호작용을 만들 때 |
| 미디어 연결(대표 이미지, 본문 이미지) | 미디어를 만들 때 |
| 글 목록 정렬과 페이지 크기 | P4에서 정한다 |
