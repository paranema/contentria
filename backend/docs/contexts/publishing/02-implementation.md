# 출판 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 각 규칙을 어느 계층과 컴포넌트에 배치해 구현했는지 기록합니다.
제품의 비즈니스 규칙이 변경되면 `01-rules.md`를, 계층 배치나 기술적 구현 방식이 변경되면 이 문서를 수정합니다.

- 내부 계층 구조는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **헥사고날 아키텍처** 규칙을 따릅니다.
- 도메인 불변식과 애플리케이션 정책을 나누는 기준은 회원 컨텍스트와 동일합니다([member/02-implementation.md](../member/02-implementation.md)의 "도메인 불변식과 애플리케이션 정책의 구분 기준" 참고). 단일 애그리거트 상태만으로 판단할 수 있으면 도메인(`도메인`), 다른 애그리거트나 외부 컨텍스트를 조회해야 하면 응용 계층(`앱`)에서 처리합니다.

## 애그리거트 경계 및 도메인 모델

출판 컨텍스트는 블로그(`Blog`), 카테고리 트리(`CategoryTree`), 게시글(`Post`) 세 개의 애그리거트로 구성됩니다.

| 애그리거트     | 애그리거트 루트 | 내부 구성 요소 | 식별자(PK)                                  |
| -------------- | --------------- | -------------- | ------------------------------------------- |
| `Blog`         | `Blog`          | 없음           | 블로그 ID                                   |
| `CategoryTree` | `CategoryTree`  | `Category`     | 블로그 ID와 동일한 값 (블로그당 1개)        |
| `Post`         | `Post`          | `PostSlug`     | 게시글 ID                                   |

- **카테고리는 개별 항목이 아니라 블로그의 트리 전체(`CategoryTree`)가 하나의 애그리거트입니다.** 최대 2단계 깊이 제한, 동일 부모 아래 이름 중복 금지, 하위 카테고리가 남은 상태에서의 삭제 금지 규칙은 모두 트리 전체를 함께 보아야 판단할 수 있으며, 편집 화면 역시 트리 전체를 한 번에 저장합니다([strategic/03-bounded-contexts.md](../../strategic/03-bounded-contexts.md)의 `Category` 애그리거트는 이 트리를 가리킵니다).
- **이전 slug(`PostSlug`)는 게시글(`Post`) 애그리거트의 내부 멤버입니다.** 이전 slug는 해당 게시글로 연결되는 리다이렉트 경로이며 게시글이 삭제되면 함께 삭제됩니다.
  - 현재 slug는 `posts.slug` 컬럼에 두고, 해당 게시글이 사용한 적 있는 모든 slug(현재 slug 포함)는 `post_slugs` 테이블에 보관합니다. 즉 이전 slug는 `post_slugs`에서 현재 slug를 제외한 나머지입니다.
  - `post_slugs(blog_id, slug)`에 고유 제약(`UNIQUE`)을 설정하여 블로그 내에서 현재 slug와 이전 slug가 겹치지 않는다는 규칙을 데이터베이스가 최종 보증합니다.
  - 별도의 '현재 여부(`is_current`)' 컬럼은 두지 않습니다. 이전 slug로 복원할 때 두 행의 플래그를 맞바꾸면 UPDATE 실행 순서에 따라 순간적으로 현재 행이 2개가 되어 부분 고유 인덱스에 걸릴 수 있기 때문입니다. 플래그 컬럼 없이 `posts.slug` 값만 변경하면 이 문제를 피할 수 있습니다.
- **게시글은 작성자(블로그 소유자) ID를 중복 저장하지 않습니다.** 게시글은 `blog_id`만 보유하며, 소유권 확인은 블로그를 조회해 판단합니다. 1인 블로그이므로 작성자는 항상 블로그 소유자와 같으며, 게시글에 소유자 ID를 복제해 두면 두 값이 어긋날 위험만 생깁니다.

### 한 트랜잭션에서 여러 애그리거트를 생성하는 예외

블로그를 개설할 때는 `Blog`, `CategoryTree`, 그리고 샘플 `Post` 2개를 단일 트랜잭션 안에서 함께 생성합니다.
"한 트랜잭션에는 하나의 애그리거트만 수정한다"는 원칙은 기존 애그리거트를 동시에 **수정**할 때 발생하는 잠금 경합을 피하기 위한 규칙이므로, 처음 생성(`INSERT`)하는 애그리거트들을 묶는 것은 다른 트랜잭션과 경합을 일으키지 않습니다.

## 데이터베이스 스키마 및 제약 조건

`publishing` 스키마에서 블로그·카테고리·게시글 slug의 유일성을 보장하기 위해 설정한 DB 제약 조건입니다.

| 제약 조건                                                        | 보장하는 도메인 규칙                                             |
| ---------------------------------------------------------------- | ---------------------------------------------------------------- |
| `blogs(slug)` UNIQUE                                             | 플랫폼 전체에서 블로그 slug의 유일성 보장                        |
| `categories(tree_id, slug)` UNIQUE (`DEFERRABLE INITIALLY DEFERRED`) | 블로그(`tree_id`) 내에서 카테고리 slug의 유일성 보장             |
| `categories(tree_id, parent_id, name)` 제약 미설정               | 대소문자 구분 없는 이름 중복 검사는 `CategoryTree` 도메인에서 수행|
| `post_slugs(blog_id, slug)` UNIQUE                               | 블로그 내에서 현재 slug와 이전 slug 전체의 유일성 보장           |

- **카테고리 slug 고유 제약과 부모 자기참조 외래키를 커밋 시점(`DEFERRABLE INITIALLY DEFERRED`)에 검사하는 이유**: Hibernate는 한 번의 flush 안에서 `INSERT`를 `DELETE`보다 먼저 실행하고, 같은 엔티티끼리는 추가된 순서대로 삽입합니다. 따라서 트리를 교체 저장하면서 기존 `x` 카테고리를 지우고 새 `x` 카테고리를 추가하거나, 새 하위 카테고리를 새 상위 카테고리보다 먼저 넣으면 즉시 검사(Immediate) 제약은 커밋 전에 실패합니다(`MigrationMatchesMappingTest`에서 두 케이스를 검증합니다).

외래키(FK)는 동일한 애그리거트 내부 테이블 사이에만 설정하고 애그리거트 간에는 설정하지 않습니다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "외래키" 절 참고).

| 외래키 경로                                                                      | 설정 여부 | 이유                  |
| -------------------------------------------------------------------------------- | --------- | --------------------- |
| `categories.tree_id → category_trees.id`                                         | 설정함    | 동일 애그리거트 내부  |
| `categories.parent_id → categories.id`                                           | 설정함    | 동일 애그리거트 내부  |
| `post_slugs.post_id → posts.id`                                                  | 설정함    | 동일 애그리거트 내부  |
| `posts.blog_id → blogs.id`, `posts.category_id → categories.id`, `category_trees.id → blogs.id` | 설정 안 함| 서로 다른 애그리거트 간|

### 애플리케이션 사전 확인과 DB 제약이 함께 동작하는 규칙

애플리케이션의 사전 조회와 동시 요청 경합 시의 데이터베이스 제약 동작은 다음과 같이 나뉩니다.

| 규칙                          | 애플리케이션의 사전 처리                                    | 동시 요청 경합 시 동작                                                                                              |
| ----------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 블로그 slug 중복 금지         | 저장 전 중복 여부를 조회해 명확한 비즈니스 오류를 반환합니다.| DB 고유 제약이 두 번째 요청을 차단하며 `BLOG_SLUG_TAKEN`으로 변환해 응답합니다.                                     |
| 회원당 블로그 최대 3개        | 저장 전 해당 회원의 보유 블로그 수를 조회합니다.             | 별도 잠금을 걸지 않습니다. 극단적인 동시 요청으로 4개가 생성될 가능성은 실용적으로 수용합니다.                      |
| 게시글 slug 접미사(`-2`, `-3`)| 블로그 내 동일 접두어 slug 목록을 조회해 다음 번호를 부여합니다.| 동시에 같은 번호를 고르면 고유 제약이 두 번째 요청을 차단하며, 재시도하면 다음 번호가 부여됩니다.                   |
| 게시글이 속한 카테고리 삭제 금지| 삭제 전 해당 카테고리에 속한 게시글 존재 여부를 조회합니다.  | 삭제 직전 동시에 해당 카테고리로 저장된 게시글은 존재하지 않는 카테고리를 가리키게 되며, 조회 시 '분류 없음'으로 처리합니다.|

## 발행 도메인 이벤트 및 아웃박스 구성

게시글의 생명주기 변화에 따라 외부 컨텍스트로 발행하는 도메인 이벤트 목록입니다.

| 이벤트          | 발행 시점                                    | 구독 컨텍스트 | 페이로드 구성                                    |
| --------------- | -------------------------------------------- | ------------- | ------------------------------------------------ |
| `PostPublished` | 게시글 최초 발행 시 (비공개 후 재공개는 제외)| 알림          | 게시글 ID, 블로그 ID, 블로그 소유자 ID, 발행 시각|
| `PostDeleted`   | 게시글 삭제 시                               | 상호작용      | 게시글 ID, 블로그 ID                             |

- 모든 도메인 이벤트는 상태를 변경하는 로컬 트랜잭션 안에서 `publishing.outbox_event` 테이블에 함께 기록하며([architecture/03-context-integration.md](../../architecture/03-context-integration.md) 참고), 트랜잭션이 롤백되면 이벤트 기록도 함께 롤백됩니다.
- `PostPublished` 이벤트에 블로그 소유자 ID를 함께 담는 이유는, 알림 컨텍스트가 "누구의 블로그인지" 확인하기 위해 출판 컨텍스트를 다시 역호출하지 않도록 하기 위함입니다(출판 컨텍스트는 발행 시점에 이미 블로그 엔티티를 조회한 상태입니다).

아웃박스 이벤트 기록을 담당하는 포트·어댑터 및 테이블 레코드 구성은 다음과 같습니다.

| 구현 요소        | 위치 및 방식                                                                                                                  |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| 포트와 어댑터    | `application/required/PostEvents` 포트를 `adapter/required/messaging/OutboxPostEvents`가 구현합니다.                          |
| 아웃박스 기록기  | `platform/messaging`의 `OutboxWriter`를 사용합니다. 활성 트랜잭션이 없으면 기록을 거부하며 JPA 트랜잭션의 JDBC 커넥션을 공유합니다. |
| 공개 이벤트 계약 | `publishing-api`의 `event/PublishingEvents.kt` (`PublishingTopics.POST`, `PostPublished`, `PostDeleted`)                      |
| 레코드 구성      | `aggregate_type`은 `"post"`(토픽 `publishing.post`), `aggregate_id`는 게시글 ID(Kafka 메시지 키), 페이로드는 JSON 문자열      |

## 컨텍스트 간 연동 인터페이스

출판 컨텍스트가 회원·미디어 컨텍스트를 호출하는 아웃바운드 연동과, 상호작용·분석 컨텍스트에 제공하는 인바운드 공개 쿼리입니다.

| 연동 방향        | 포트 및 구현 위치                                                                                                               | 동작 방식                                                                                                                   |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| 출판 → 회원 조회 | 출판 내부 포트 `BloggerStatusPort.canPublish(bloggerId)`를 `adapter/required/member`가 구현하며, 내부에서 `member-api`의 `GetAccountStatusQuery`를 호출합니다. | **쓰기 트랜잭션을 시작하기 전에** 호출하여, 향후 원격 호출로 전환되더라도 외부 응답을 기다리는 동안 DB 커넥션을 점유하지 않습니다. |
| 출판 → 미디어 연결| `application/service/PostImageLinks` 및 `adapter/required/media/MediaPostImages`를 통해 `media-api`를 호출합니다.               | 게시글 저장·삭제 트랜잭션이 **커밋된 이후**에 본문 이미지 목록과 첨부 영상(`posts.video_id`) 연결을 동기화합니다. 연결 호출이 실패해도 게시글 저장 자체는 성공으로 처리합니다([media/02-implementation.md](../media/02-implementation.md) 참고). |
| 상호작용·분석 → 출판 조회| `publishing-api`의 `GetPostReferencesQuery`, `GetBlogReferencesQuery`, `GetPostOverviewsQuery`를 `ReferenceQueryService`가 구현합니다. | 타 컨텍스트 번역 계층이나 `apps/api` 대시보드 조합 계층이 호출합니다. 자체 읽기 전용 트랜잭션에서 동작하며 무거운 본문 컬럼은 읽지 않습니다. |

- 회원 컨텍스트에 존재하지 않는 계정 ID이거나 향후 서비스 분리 시 회원 서비스가 응답하지 않는 경우에도 안전을 위해 쓰기를 거부(`쓸 수 없음`)합니다. 차단된 정상 요청은 사용자가 재시도할 수 있지만, 정지된 계정의 쓰기를 통과시키면 사후에 되돌리기 어렵기 때문입니다.

## 도메인 규칙과 엔티티 메서드 매핑

[01-rules.md](01-rules.md)에 정의된 도메인 불변식을 보장하는 애그리거트 루트 및 값 객체의 메서드 매핑입니다.

| `01-rules.md`의 규칙                         | 담당 도메인 메서드                                                      | 구현 방식 및 비고                                                                                           |
| -------------------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| 블로그 slug 형식 검증 및 변경 불가           | `Blog.create(ownerId, slug, title, description)`                        | `slug` 프로퍼티는 변경 메서드를 제공하지 않습니다.                                                          |
| 블로그 제목·설명 수정                        | `Blog.changeSettings(title, description)`                               | 소유자 일치 여부는 유스케이스에서 검증합니다.                                                               |
| 카테고리 깊이·이름 중복·하위 존재 시 삭제 금지| `CategoryTree.replaceWith(edit)`                                        | 트리 전체를 입력받아 검증 및 교체합니다(삭제 대상 카테고리의 게시글 존재 여부는 유스케이스가 선행 확인).    |
| 카테고리 slug 생성                           | `CategoryTree` 내부에서 카테고리 이름 기반으로 생성                     | 동일 트리 내에서 겹치면 번호 접미사를 부여합니다.                                                           |
| 신규 게시글 임시저장 생성                    | `Post.draft(blogId, title, body, summary, categoryId, slug)`            | 블로그 내에서 유일한 slug를 유스케이스가 계산해 전달합니다.                                                 |
| 발행, 비공개 전환, 재공개                    | `Post.publish(now)`, `Post.hide()`, `Post.republish()`                  | 각각 `DRAFT`, `PUBLISHED`, `PRIVATE` 상태에서만 허용되며, `publish` 호출 시에만 `PostPublished`를 발행합니다.|
| 발행 전 제목 수정 시 slug 자동 갱신          | `Post.revise(content, categoryId, draftSlug)`                           | `draftSlug`는 `DRAFT` 상태일 때만 반영됩니다.                                                               |
| 발행 후 slug 변경, 이력 보존, 이전 slug 복원 | `Post.changeSlug(newSlug)`                                              | 새 slug를 `post_slugs`에 추가하고 `posts.slug`를 갱신합니다(자신의 이전 slug로 복원할 때는 중복 추가하지 않음).|
| slug 문자열 변환 및 중복 접미사 계산         | `Slugs.from(text, fallback, maxLength)`, `Slugs.nextAvailable(base, taken, maxLength)` | 순수 함수로 구현하며, 이미 사용 중인 slug 집합(`taken`)은 유스케이스가 조회해 전달합니다.                   |
| 게시글 요약 생성 구분                        | `PostSummary.written(text)`, `PostSummary.derived(text)`                | 도메인은 300자 제한과 직접 작성 여부만 관리하며, 마크다운 본문에서 평문을 추출하는 작업은 응용 계층이 수행합니다.|

## 유스케이스 및 HTTP 엔드포인트

`application/provided` 패키지에 선언된 인바운드 유스케이스 인터페이스와 구현 서비스, 그리고 매핑되는 HTTP 경로입니다.

| 도메인 흐름                 | 유스케이스 인터페이스               | 구현 서비스            | HTTP 엔드포인트 및 응답                                                                                  |
| --------------------------- | ----------------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------- |
| 흐름 1 블로그 개설          | `CreateBlogUseCase`                 | `BlogService`          | `POST /blogs` → `201 Created`                                                                            |
| 블로그 설정 수정            | `ChangeBlogSettingsUseCase`         | `BlogService`          | `PATCH /blogs/{slug}` (소유자 전용)                                                                      |
| 블로그 조회                 | `GetBlogQuery`                      | `BlogService`          | `GET /blogs/{slug}` (전체 공개), `GET /me/blogs` (내 블로그 목록)                                        |
| 흐름 2 카테고리 트리 조회   | `GetCategoryTreeQuery`              | `CategoryTreeService`  | `GET /blogs/{slug}/categories` (전체 공개, 트리와 현재 버전 반환)                                        |
| 흐름 2 카테고리 트리 편집   | `EditCategoryTreeUseCase`           | `CategoryTreeService`  | `PUT /blogs/{slug}/categories` (소유자 전용, 트리 전체와 편집 시작 시점의 버전 전달)                     |
| 흐름 3 게시글 작성          | `WritePostUseCase`                  | `PostService`          | `POST /blogs/{slug}/posts` → `201 Created` (`DRAFT` 상태)                                                |
| 흐름 3 게시글 수정          | `RevisePostUseCase`                 | `PostService`          | `PUT /posts/{id}`                                                                                        |
| 게시글 slug 변경            | `ChangePostSlugUseCase`             | `PostService`          | `PUT /posts/{id}/slug` (`slug` 값을 비우면 현재 제목에서 재생성)                                         |
| 흐름 4 발행·비공개·재공개   | `ChangePostStatusUseCase`           | `PostService`          | `POST /posts/{id}/publish`, `/hide`, `/republish`                                                        |
| 흐름 5 게시글 삭제          | `DeletePostUseCase`                 | `PostService`          | `DELETE /posts/{id}` → `204 No Content`                                                                  |
| 소유자용 내 게시글 조회     | `GetMyPostsQuery`                   | `PostService`          | `GET /me/posts/{id}`, `GET /me/blogs/{slug}/posts?page=` (`DRAFT`·`PRIVATE` 포함, 최근 수정 순)          |
| 블로그별 발행 게시글 목록   | `GetPublishedPostsQuery.inBlog`     | `PublishedPostService` | `GET /blogs/{slug}/posts?category=&page=`                                                                |
| 플랫폼 홈 최근 발행 글 목록 | `GetPublishedPostsQuery.recent`     | `PublishedPostService` | `GET /posts?page=`                                                                                       |
| 발행된 게시글 제목 일괄 조회| `GetPublishedPostsQuery.titles`     | `PublishedPostService` | `GET /posts/titles?ids=a,b` (최대 100개, 비로그인 허용, `PUBLISHED` 상태인 글만 반환하여 비공개 글 제목 노출 방지) |
| 공개 게시글 상세 및 리다이렉트| `GetPublishedPostsQuery.find`     | `PublishedPostService` | `GET /blogs/{slug}/posts/{postSlug}` → 현재 slug면 `200 OK`, 이전 slug면 `301 Moved Permanently`         |
| 타 컨텍스트용 참조 조회 (`-api`)| `GetPostReferencesQuery`, `GetBlogReferencesQuery`, `GetPostOverviewsQuery` | `ReferenceQueryService`| 상호작용·분석 컨텍스트 및 대시보드 조합 계층에서 호출                                                    |

- `apps/api`의 보안 필터 체인은 `/blogs/` 하위의 `GET` 요청과 `GET /posts` 요청을 비로그인 독자에게 열어두며, 해당 공개 경로는 항상 `PUBLISHED` 상태인 게시글만 반환합니다.

## 세부 구현 결정

카테고리 트리 동시성 제어, 마크다운 파싱, 목록 페이지네이션 등 출판 컨텍스트 내부에서 확정한 기술적 세부 결정 사항입니다.

| 항목                       | 구현 상세                                                                                                                                      |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 규칙 검증 순서             | `01-rules.md`의 조건 표 순서대로 검사하여 가장 먼저 걸리는 오류를 반환합니다. 블로그 개설은 `계정 상태(트랜잭션 외부) → 보유 블로그 수 → 형식·예약어 → 중복` 순서이며, 카테고리 편집은 `소유권 → 트리 불변식(2~4번) → 게시글 존재 여부(5번)` 순서입니다. |
| 카테고리 트리 낙관적 잠금  | 편집 화면을 열 때 받은 버전과 현재 버전이 다르면 `409 CATEGORY_TREE_CHANGED`를 반환합니다. `Category`는 연관관계의 역방향이므로 하위 카테고리만 수정·추가되어도 루트인 `CategoryTree`의 버전이 반드시 올라가도록 Named Query에서 `OPTIMISTIC_FORCE_INCREMENT`로 조회합니다. |
| 카테고리 편집 응답         | 버전 번호는 트랜잭션 커밋 시점에 증가하므로, 커밋 완료 후 트리를 다시 조회해 최신 버전과 함께 반환합니다.                                      |
| 마크다운 본문 요약 추출    | `commonmark` AST에서 순수 텍스트 노드만 수집하며 링크 URL, 이미지, 코드 블록, 인라인 HTML은 제외합니다.                                        |
| 샘플 게시글 본문 템플릿    | `publishing-core`의 `samples/*.md` 리소스 파일을 읽어 생성하며, 제목은 별도 필드로 들어가므로 본문 파일에는 제목 헤더를 중복 기재하지 않습니다.|
| 타인 리소스 접근 응답 구분 | 게시글 ID로 직접 요청하는 수정·삭제·조회는 소유자가 아니면 상태와 무관하게 `404 POST_NOT_FOUND`를 반환해 임시저장 글의 존재 자체를 숨깁니다. 반면 블로그 자체는 공개 자원이므로 타인의 블로그에 글을 쓰려 하면 `403 NOT_BLOG_OWNER`를 반환합니다. |
| 사용 중인 slug 조회        | 해당 블로그의 `post_slugs`에서 동일 접두어로 시작하는 slug들을 조회(`Post.findSlugsTakenInBlog`)하되, 현재 게시글 자신의 slug들은 제외하여 이전 slug로 되돌아갈 수 있게 합니다. |
| 임시저장 글의 slug 유지    | `DRAFT` 게시글이라도 본문만 수정하고 제목이 바뀌지 않았으면 작성자가 수동 지정한 slug를 덮어쓰지 않고 그대로 유지합니다.                       |
| 목록 페이지네이션          | 페이지당 20건씩 오프셋 방식(`?page=0`부터 시작)으로 조회하며 `items`, `page`, `totalItems`, `totalPages`를 반환합니다. 범위를 벗어난 페이지 번호는 빈 목록을, 음수 페이지는 `400 INVALID_PAGE`를 반환합니다. |
| 오프셋 방식을 선택한 이유  | 초기에는 커서(Keyset) 방식을 적용했으나 UI의 페이지 번호 이동 요구사항에 맞춰 오프셋 방식으로 전환했습니다. 1인 블로그 규모의 게시글 수에서는 오프셋 비용이 문제 되지 않으며, 향후 무한 스크롤로 전환되는 목록만 커서 방식으로 분리할 수 있습니다. |
| 목록 정렬 및 프로젝션      | 공개 목록과 홈 목록은 `발행 시각 최신순(동일 시각이면 ID 역순)`, 소유자 관리 목록은 `최근 수정 순`으로 정렬합니다. 목록 조회 시에는 무거운 `body` 컬럼을 읽지 않도록 Named Query에서 필요한 컬럼만 선택해 `PostRow` 프로젝션으로 매핑합니다. |
| 이전 slug 리다이렉트 응답  | `301` 상태 코드와 `Location` 헤더(현재 주소)를 반환하며, SPA 프론트엔드가 자체 라우팅에 활용할 수 있도록 응답 본문에도 블로그 slug와 현재 게시글 slug를 함께 담습니다. |
| 게시글 동시 수정 정책      | 1인 블로그 특성상 동일 게시글을 여러 사람이 동시에 편집할 일이 없으므로 별도의 낙관적 잠금 버전을 두지 않고 마지막 저장 요청을 반영합니다.     |

## 보류

아직 구현하지 않고 화면 요구사항이 구체화될 때까지 보류한 기능입니다.

| 항목             | 상태 및 사유                                                                  |
| ---------------- | ----------------------------------------------------------------------------- |
| 카테고리별 글 수 | 블로그 사이드바의 카테고리 옆 게시글 수 표시가 화면에서 필요해질 때 추가합니다.|
