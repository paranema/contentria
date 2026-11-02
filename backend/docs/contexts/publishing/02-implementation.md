# 출판 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 블로그 개설, 카테고리 트리 편집, 게시글 작성·발행·삭제 및 slug 이력 관리 규칙을 실제 코드 계층과 데이터베이스에 어떻게 배치해 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

출판 컨텍스트(`contexts/publishing`)는 핵심 도메인 모델의 불변식을 외부 기술 의존성으로부터 보호하기 위해 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **헥사고날 아키텍처**를 따릅니다. 단일 애그리거트 상태만으로 판단할 수 있으면 도메인(`도메인`)에서 처리하고, 다른 애그리거트나 외부 컨텍스트를 조회해야 하면 응용 계층(`앱`)에서 조율합니다([member/02-implementation.md](../member/02-implementation.md) 참고).

외부 컨텍스트와의 연동 및 도메인 이벤트 발행을 담당하는 포트와 어댑터 구성은 다음과 같습니다.

| 연동 방향 및 역할         | 포트 및 구현 위치                                                                                  | 동작 방식                                                                                                                                          |
| ------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 출판 → 회원 상태 조회     | `application/required/BloggerStatusPort` → `adapter/required/member` (`member-api` 호출)           | **쓰기 트랜잭션을 시작하기 전에** 호출하여 외부 응답 대기 중 DB 커넥션을 점유하지 않습니다(회원 서비스 미응답 시에도 안전을 위해 쓰기 거부).       |
| 출판 → 미디어 연결 동기화 | `application/service/PostImageLinks` → `adapter/required/media/MediaPostImages` (`media-api` 호출) | 게시글 저장·삭제 트랜잭션이 **커밋된 이후**에 본문 이미지 목록과 첨부 영상(`posts.video_id`) 연결을 동기화합니다(연결 실패 시에도 글 저장은 성공). |
| 출판 → 도메인 이벤트 발행 | `application/required/PostEvents` → `adapter/required/messaging/OutboxPostEvents` (`OutboxWriter`) | 비즈니스 트랜잭션 안에서 `publishing.outbox_event`에 `PostPublished`·`PostDeleted`를 기록합니다(토픽 `publishing.post`, 파티션 키는 게시글 ID).    |
| 타 컨텍스트 → 출판 조회   | `publishing-api`의 `GetBlogReferencesQuery`, `GetPostReferencesQuery`, `GetPostOverviewsQuery`     | `ReferenceQueryService`가 구현하며 상호작용·분석 컨텍스트 및 대시보드 조합 계층이 호출합니다(자체 읽기 전용 트랜잭션, 무거운 `body` 컬럼 제외).    |

### 스프링 설정 구성

스프링 설정은 두 클래스로 분리되어 있으며, API 서버(`apps/api`)에서 로드됩니다.

| 설정 클래스                   | 등록 대상 컴포넌트                                                                                    | 로드 프로세스 |
| ----------------------------- | ----------------------------------------------------------------------------------------------------- | ------------- |
| `PublishingCoreConfiguration` | 엔티티, 리포지토리, 유스케이스 서비스(`application`), 아웃바운드 어댑터, 아웃박스(`publishingOutbox`) | `apps/api`    |
| `PublishingWebConfiguration`  | 블로그·카테고리·게시글 REST 컨트롤러(`adapter/provided/web`)                                          | `apps/api`    |

## 데이터 모델

### 애그리거트 구성

출판 컨텍스트는 블로그(`Blog`), 카테고리 트리(`CategoryTree`), 게시글(`Post`) 세 개의 애그리거트로 구성됩니다.

| 애그리거트 (`publishing` 스키마)  | 내부 구성 요소            | 식별자(PK)                           | 설계 특징                                                                                                           |
| --------------------------------- | ------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| `Blog` (`blogs`)                  | 없음                      | 블로그 ID                            | 블로그의 고유 주소(`slug`)와 소유자 ID, 제목·설명을 보유합니다.                                                     |
| `CategoryTree` (`category_trees`) | `Category` (`categories`) | 블로그 ID와 동일한 값 (블로그당 1개) | 최대 2단계 깊이·동일 부모 내 이름 중복·하위 존재 시 삭제 금지 규칙을 트리 전체 단위로 검증하고 한 번에 저장합니다.  |
| `Post` (`posts`)                  | `PostSlug` (`post_slugs`) | 게시글 ID                            | `ownerId`를 중복 저장하지 않고 `blog_id`만 보유하며, 현재 slug와 과거 사용한 모든 slug를 `post_slugs`에 보관합니다. |

이전 slug(`PostSlug`)는 게시글(`Post`)의 내부 멤버이며 게시글이 삭제되면 함께 삭제됩니다. 현재 slug는 `posts.slug` 컬럼에 두고 해당 게시글이 사용한 적 있는 모든 slug(현재 slug 포함)는 `post_slugs` 테이블에 보관합니다. 별도의 '현재 여부(`is_current`)' 플래그 컬럼을 두면 이전 slug 복원 시 두 행의 플래그를 맞바꾸는 과정에서 부분 고유 인덱스에 걸릴 수 있으므로, 플래그 없이 `posts.slug` 값만 변경해 이 문제를 피합니다.

블로그를 개설할 때는 `Blog`, `CategoryTree`, 그리고 샘플 `Post` 2개를 단일 트랜잭션 안에서 함께 생성합니다. "한 트랜잭션에는 하나의 애그리거트만 수정한다"는 원칙은 기존 애그리거트를 동시에 수정할 때 발생하는 잠금 경합을 피하기 위한 것이므로, 최초로 함께 생성(`INSERT`)하는 애그리거트들을 묶는 것은 경합을 일으키지 않습니다.

### 데이터베이스 스키마

`publishing` 스키마에서 주소(slug)의 유일성을 보장하기 위해 설정한 제약 조건과 외래키 구성입니다. 외래키(FK)는 동일한 애그리거트 내부 테이블 사이에만 설정하고 서로 다른 애그리거트 간(`posts.blog_id`, `posts.category_id`, `category_trees.id`)에는 설정하지 않습니다.

| 테이블 및 경로                                                     | 제약 조건                                                    | 설정 이유 및 보장하는 도메인 규칙                                               |
| ------------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| `blogs(slug)`                                                      | `UNIQUE`                                                     | 플랫폼 전체에서 블로그 slug의 유일성 보장                                       |
| `categories(tree_id, slug)`                                        | `UNIQUE DEFERRABLE INITIALLY DEFERRED`                       | 블로그(`tree_id`) 내에서 카테고리 slug의 유일성 보장 (커밋 시점 지연 검사)      |
| `categories(tree_id, parent_id, name)`                             | DB 제약 미설정                                               | 대소문자 구분 없는 동일 부모 내 이름 중복 검사는 `CategoryTree` 도메인에서 수행 |
| `post_slugs(blog_id, slug)`                                        | `UNIQUE`                                                     | 블로그 내에서 현재 slug와 이전 slug 전체의 유일성 보장                          |
| `categories.tree_id`, `categories.parent_id`, `post_slugs.post_id` | 애그리거트 내부 외래키(FK) 설정 (`parent_id`는 `DEFERRABLE`) | 동일 애그리거트 루트와 내부 엔티티 간의 참조 무결성 보장                        |

카테고리 slug 고유 제약과 부모 자기참조 외래키를 커밋 시점(`DEFERRABLE INITIALLY DEFERRED`)에 검사하는 이유는, Hibernate가 한 번의 flush 안에서 `INSERT`를 `DELETE`보다 먼저 실행하고 같은 엔티티끼리는 추가된 순서대로 삽입하기 때문입니다. 트리를 교체 저장하면서 기존 `x` 카테고리를 지우고 새 `x` 카테고리를 추가하거나 새 하위 카테고리를 상위보다 먼저 넣으면 즉시 검사(Immediate) 제약은 커밋 전에 실패합니다(`MigrationMatchesMappingTest`에서 검증).

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 다섯 가지 비즈니스 흐름을 처리하는 도메인 메서드와 응용 계층의 세부 구현입니다. 모든 흐름은 `01-rules.md`의 조건 표 순서대로 검증하여 가장 먼저 걸리는 오류를 반환합니다.

### 흐름 1·2. 블로그 개설·카테고리 편집

애플리케이션의 사전 조회와 동시 요청 경합 시의 데이터베이스 제약 분담, 그리고 도메인 메서드 매핑은 다음과 같습니다.

| 규칙 및 도메인 메서드                            | 애플리케이션 및 도메인의 처리                                                      | 동시성 제어 및 경합 시 동작                                                                                            |
| ------------------------------------------------ | ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `Blog.create(ownerId, slug, title, description)` | 계정 활성 확인(트랜잭션 외부) → 보유 블로그 수(3개) 조회 → 형식·예약어·중복 확인   | `slug` 중복 경합 시 DB 고유 제약이 차단(`BLOG_SLUG_TAKEN`). 3개 상한은 별도 잠금을 두지 않으며 극단적 동시 생성은 수용 |
| `Blog.changeSettings(title, description)`        | 유스케이스에서 소유자 일치 확인 후 제목·설명 변경 (`slug`는 변경 메서드 미제공)    | -                                                                                                                      |
| `CategoryTree.replaceWith(edit)`                 | 소유권 확인 → 트리 불변식(깊이·이름 중복·하위 존재) 검증 → 삭제 대상의 게시글 확인 | 삭제 직전 동시에 해당 카테고리로 저장된 글은 조회 시 '분류 없음'으로 처리                                              |

카테고리 트리는 편집 화면을 열 때 받은 버전과 현재 버전이 다르면 `409 CATEGORY_TREE_CHANGED`를 반환합니다. `Category`는 연관관계의 역방향이므로 하위 카테고리만 수정·추가되어도 루트인 `CategoryTree`의 버전이 반드시 올라가도록 Named Query에서 `OPTIMISTIC_FORCE_INCREMENT`로 조회하며, 커밋 완료 후 트리를 다시 조회해 증가된 최신 버전과 함께 반환합니다. 샘플 게시글 본문은 `publishing-core`의 `samples/*.md` 리소스 파일을 읽어 생성합니다.

### 흐름 3·4·5. 게시글 작성·발행·삭제

게시글 생명주기와 slug·요약 계산을 담당하는 도메인 메서드 매핑입니다. 1인 블로그 특성상 동일 게시글을 여러 사람이 동시에 편집할 일이 없으므로 게시글에는 별도의 낙관적 잠금 버전을 두지 않습니다.

| 담당 도메인 메서드                                           | 검증 및 구현 상세                                                                                                                                                     |
| ------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Post.draft(blogId, title, body, summary, categoryId, slug)` | 유스케이스가 블로그 내 유일한 slug를 계산해 전달하며 `DRAFT` 상태로 생성합니다.                                                                                       |
| `Post.revise(content, categoryId, draftSlug)`                | 본문·제목을 수정합니다. `DRAFT` 게시글이라도 제목이 바뀌지 않았으면 작성자가 수동 지정한 slug를 덮어쓰지 않고 유지합니다.                                             |
| `Post.publish(now)`, `Post.hide()`, `Post.republish()`       | 각각 `DRAFT`, `PUBLISHED`, `PRIVATE` 상태에서만 허용되며, 최초 발행(`publish`) 시에만 `PostPublished`(소유자 ID 포함)를 아웃박스에 기록합니다.                        |
| `Post.changeSlug(newSlug)`                                   | 새 slug를 `post_slugs`에 추가하고 `posts.slug`를 갱신합니다(자신의 이전 slug로 복원할 때는 중복 추가하지 않음).                                                       |
| `Slugs.from(...)`, `Slugs.nextAvailable(...)`                | 순수 함수로 구현하며, 해당 블로그의 `post_slugs`에서 동일 접두어 slug 목록(`Post.findSlugsTakenInBlog`, 현재 글 자신은 제외)을 조회해 전달합니다.                     |
| `PostSummary.written(text)`, `PostSummary.derived(text)`     | 도메인은 300자 제한과 직접 작성 여부만 관리하며, `commonmark` AST에서 순수 텍스트 노드만 추출(링크 URL·이미지·코드 블록·HTML 제외)하는 작업은 응용 계층이 수행합니다. |

### HTTP 엔드포인트

`application/provided` 패키지에 선언된 인바운드 유스케이스 인터페이스와 구현 서비스, 그리고 매핑되는 HTTP 경로입니다. `apps/api`의 보안 필터 체인은 `/blogs/` 하위의 `GET` 요청과 `GET /posts` 요청을 비로그인 독자에게 열어두며, 해당 공개 경로는 항상 `PUBLISHED` 상태인 게시글만 반환합니다.

| 도메인 흐름                    | 유스케이스 인터페이스           | 구현 서비스            | HTTP 엔드포인트 및 응답                                                                                |
| ------------------------------ | ------------------------------- | ---------------------- | ------------------------------------------------------------------------------------------------------ |
| 흐름 1 블로그 개설             | `CreateBlogUseCase`             | `BlogService`          | `POST /blogs` → `201 Created`                                                                          |
| 블로그 설정 수정               | `ChangeBlogSettingsUseCase`     | `BlogService`          | `PATCH /blogs/{slug}` (소유자 전용)                                                                    |
| 블로그 조회                    | `GetBlogQuery`                  | `BlogService`          | `GET /blogs/{slug}` (전체 공개), `GET /me/blogs` (내 블로그 목록)                                      |
| 흐름 2 카테고리 트리 조회      | `GetCategoryTreeQuery`          | `CategoryTreeService`  | `GET /blogs/{slug}/categories` (전체 공개, 트리와 현재 버전 반환)                                      |
| 흐름 2 카테고리 트리 편집      | `EditCategoryTreeUseCase`       | `CategoryTreeService`  | `PUT /blogs/{slug}/categories` (소유자 전용, 트리 전체와 편집 시작 버전 전달)                          |
| 흐름 3 게시글 작성             | `WritePostUseCase`              | `PostService`          | `POST /blogs/{slug}/posts` → `201 Created` (`DRAFT` 상태)                                              |
| 흐름 3 게시글 수정             | `RevisePostUseCase`             | `PostService`          | `PUT /posts/{id}`                                                                                      |
| 게시글 slug 변경               | `ChangePostSlugUseCase`         | `PostService`          | `PUT /posts/{id}/slug` (`slug` 값을 비우면 현재 제목에서 재생성)                                       |
| 흐름 4 발행·비공개·재공개      | `ChangePostStatusUseCase`       | `PostService`          | `POST /posts/{id}/publish`, `/hide`, `/republish`                                                      |
| 흐름 5 게시글 삭제             | `DeletePostUseCase`             | `PostService`          | `DELETE /posts/{id}` → `204 No Content`                                                                |
| 소유자용 내 게시글 조회        | `GetMyPostsQuery`               | `PostService`          | `GET /me/posts/{id}`, `GET /me/blogs/{slug}/posts?page=` (`DRAFT`·`PRIVATE` 포함, 최근 수정 순)        |
| 블로그별 발행 게시글 목록      | `GetPublishedPostsQuery.inBlog` | `PublishedPostService` | `GET /blogs/{slug}/posts?category=&page=`                                                              |
| 플랫폼 홈 최근 발행 글 목록    | `GetPublishedPostsQuery.recent` | `PublishedPostService` | `GET /posts?page=`                                                                                     |
| 발행된 게시글 제목 일괄 조회   | `GetPublishedPostsQuery.titles` | `PublishedPostService` | `GET /posts/titles?ids=a,b` (최대 100개, 비로그인 허용, `PUBLISHED` 글만 반환하여 비공개 글 제목 보호) |
| 공개 게시글 상세 및 리다이렉트 | `GetPublishedPostsQuery.find`   | `PublishedPostService` | `GET /blogs/{slug}/posts/{postSlug}` → 현재 slug면 `200 OK`, 이전 slug면 `301 Moved Permanently`       |

목록 조회와 리다이렉트, 권한 오류 응답에 적용된 세부 규약은 다음과 같습니다.

| 항목                       | 응답 및 동작 상세                                                                                                                                                                                                    |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 오프셋 목록 페이지네이션   | UI의 페이지 번호 이동 요구사항에 맞춰 페이지당 20건씩 오프셋 방식(`?page=0`부터 시작)으로 조회하며 `items`, `page`, `totalItems`, `totalPages`를 반환합니다(범위 초과 시 빈 목록, 음수 페이지는 `400 INVALID_PAGE`). |
| 목록 정렬 및 프로젝션      | 공개 목록은 `발행 시각 최신순(동일 시각이면 ID 역순)`, 소유자 관리 목록은 `최근 수정 순`으로 정렬하며, 무거운 `body` 컬럼을 제외하고 `PostRow` 프로젝션으로 조회합니다.                                              |
| 이전 slug 리다이렉트 응답  | `301` 상태 코드와 `Location` 헤더(현재 주소)를 반환하며, SPA 프론트엔드가 자체 라우팅에 활용할 수 있도록 응답 본문에도 블로그 slug와 현재 게시글 slug를 함께 담습니다.                                               |
| 타인 리소스 접근 응답 구분 | 게시글 ID로 직접 요청하는 수정·삭제·조회는 소유자가 아니면 `404 POST_NOT_FOUND`로 존재를 숨기며, 공개 자원인 타인의 블로그에 글을 쓰려 하면 `403 NOT_BLOG_OWNER`를 반환합니다.                                       |
