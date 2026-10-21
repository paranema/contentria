# 레거시 분석

레거시 모듈(`blog-*`)에서 확인한 경계 문제다. [03-bounded-contexts.md](03-bounded-contexts.md)의 "결정과 이유"가 근거로 삼았다.

새 구조는 레거시와 호환하지 않고 따로 만든다. 레거시 소스는 저장소에서 지웠고 `legacy-final` 태그에 남아 있다.
아래 링크는 모두 그 태그의 코드를 가리킨다. 이 폴더에서 코드 경로를 쓰는 유일한 문서다.

## 레거시에서 확인한 경계 문제

### 출판 안의 순환과 공유 트랜잭션

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| blog, post, category가 서로의 서비스를 호출한다 | [BlogFacade.kt:6-8](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/blog/application/BlogFacade.kt#L6), [PostFacade.kt:4-5](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L4), [CategoryFacade.kt:3](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/category/application/CategoryFacade.kt#L3) | 출판 컨텍스트 내부 호출이 된다 |
| 블로그 생성이 블로그, 카테고리, 글을 한 트랜잭션에서 만든다 | [BlogFacade.kt:49-67](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/blog/application/BlogFacade.kt#L49) | 출판 내부 트랜잭션. 정상이다 |
| 가입이 User와 Credential을 한 트랜잭션에서 만든다 | [AuthFacade.kt:23-36](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/auth/application/AuthFacade.kt#L23) | 회원 내부 트랜잭션. 정상이다 |

### 작성자 개념

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 글 상세의 작성자는 블로그 소유자로 조회한다 | [PostFacade.kt:44](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L44) | 작성자는 블로그 소유자로 확정 |
| 글 수정 권한은 블로그 소유자로, 삭제 권한은 `Post.authorId`로 검사한다 | [PostFacade.kt:131](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L131), [PostFacade.kt:158](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L158), [PostService.kt:95-101](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostService.kt#L95) | 블로그 소유권 하나로 통일. 글에 작성자 ID를 두지 않는다 |
| 글 상세가 작성자를 "활성 회원"으로 조회하므로 활성이 아닌 회원의 글은 열리지 않는다 | [PostFacade.kt:44](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L44), [UserService.kt:21-28](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/user/application/UserService.kt#L21) | 글 상세는 프로필만 조회한다. 노출 여부는 03의 "정하지 않은 것"에서 정한다 |

### 회원 모델의 확산

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 이메일과 권한을 담은 `UserInfo`가 글, 블로그 응답 모델에 그대로 들어간다 | [UserInfo.kt:6-12](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/user/application/dto/UserInfo.kt#L6), [PostDetailInfo.kt:3](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/dto/PostDetailInfo.kt#L3), [BlogLayoutInfo.kt:4](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/blog/application/dto/BlogLayoutInfo.kt#L4) | 화면은 진입점에서 프로필만 조합한다 |
| auth의 응답 DTO가 user의 응답 DTO를 가져다 쓴다 | [LoginResponse.kt:4](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/auth/controller/dto/LoginResponse.kt#L4) | 회원 하나로 합쳐지며 해소 |
| auth의 `Credential`이 user의 `AuthProvider`를 쓴다 | [Credential.kt:3](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/auth/domain/Credential.kt#L3) | 회원 하나로 합쳐지며 해소 |

### 조회수와 좋아요 수

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| `Post`에 `likeCount`, `viewCount`가 있지만 값을 증가시키는 코드가 없다 | [Post.kt:43-45](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/domain/Post.kt#L43) | 글에 두 값을 두지 않는다 |
| 화면은 증가하지 않는 `likeCount`를 표시한다 | [PostCard.tsx:69](https://github.com/seung7642/contentria/blob/legacy-final/frontend/src/components/blog/PostCard.tsx#L69) | 상호작용의 좋아요 수를 쓴다 |
| 인기 글 쿼리가 posts 테이블을 직접 JOIN해 제목과 슬러그를 가져온다 | [DailyStatisticsJpaRepository.kt:76](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-common/src/main/kotlin/com/contentria/common/domain/analytics/infrastructure/DailyStatisticsJpaRepository.kt#L76) | 분석은 글 ID만 반환. 제목은 블로그 대시보드 조합에서 붙인다 |
| 분석 엔티티가 공용 라이브러리에 있다 | [VisitLog.kt](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-common/src/main/kotlin/com/contentria/common/domain/analytics/VisitLog.kt) | 분석 컨텍스트에 둔다 |

### 미디어

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 미디어가 마크다운 이미지 문법을 직접 파싱한다 | [MediaService.kt:33](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L33), [MediaService.kt:96](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L96) | 출판이 참조 파일 목록을 뽑아 넘긴다 |
| 영상 상태 전이가 세 모듈에 SQL 문자열로 흩어져 있다 | [Video.kt](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/video/domain/Video.kt), [VideoJobRepository.kt:25-63](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-worker/src/main/kotlin/com/contentria/worker/video/VideoJobRepository.kt#L25), [VideoGcJobConfig.kt:66-111](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-batch/src/main/kotlin/com/contentria/batch/jobs/video/VideoGcJobConfig.kt#L66) | 영상 모델 하나에 상태 규칙을 둔다. worker와 batch는 그 모델의 진입점이 된다 |
| 글 저장 트랜잭션 안에서 외부 저장소의 파일을 옮긴다 | [PostFacade.kt:107](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L107), [MediaService.kt:95-130](https://github.com/seung7642/contentria/blob/legacy-final/backend/blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L95) | 아래 "확인하지 못한 것" 참고 |

### 컨텍스트를 넘는 외래키

레거시 DB에는 컨텍스트를 넘는 외래키가 8개 있었다. 그중 6개가 `ON DELETE CASCADE`나 `SET NULL`로 다른 컨텍스트의 데이터를 바꿨다.
새 구조에서 컨텍스트를 넘는 외래키를 두지 않기로 한 근거다([architecture/03-context-integration.md](../architecture/03-context-integration.md)의 "외래키").
레거시 DDL은 저장소에 커밋된 적이 없다(루트 `.gitignore`의 `**/postgresql/` 규칙). 아래 표는 2026-10-18에 로컬 파일 `infra/postgresql/tables/`에서 확인한 내용이다.

| 외래키 | 방향 | 레거시에서의 동작 |
|---|---|---|
| `blogs.user_id → users` RESTRICT | 출판 → 회원 | 블로그가 있으면 회원 행을 지울 수 없다 |
| `posts.author_id → users` SET NULL | 출판 → 회원 | 회원 행 삭제 시 작성자가 비워진다 |
| `media.post_id → posts` SET NULL | 미디어 → 출판 | 글 삭제 시 이미지 연결이 해제된다 |
| `videos.post_id → posts` SET NULL | 미디어 → 출판 | 글 삭제 시 영상 연결이 해제된다 |
| `media.uploader_id → users` CASCADE | 미디어 → 회원 | 회원 행 삭제 시 이미지 행이 지워진다. 저장소의 파일은 남는다 |
| `videos.uploader_id → users` CASCADE | 미디어 → 회원 | 회원 행 삭제 시 영상 행이 지워진다 |
| `visit_logs.blog_id → blogs` CASCADE | 분석 → 출판 | 블로그 삭제 시 방문 기록이 지워진다 |
| `daily_statistics.blog_id → blogs` CASCADE | 분석 → 출판 | 블로그 삭제 시 통계가 지워진다 |

새 구조에서 같은 동작이 필요한 것은 [04-context-map.md](04-context-map.md)의 "삭제의 전파"에 드러냈다.

## 확인하지 못한 것

| 항목 | 새 구조에서 확인할 것 |
|---|---|
| 레거시에서 글 저장이 승격 뒤에 실패하면, DB의 미디어 기록은 롤백되지만 외부 저장소에서 이미 옮긴 파일은 되돌아가지 않을 것으로 보인다. 코드를 읽고 추론한 것이며 재현하지 않았다 | 새 구조는 글 저장이 파일을 옮기지 않아 이 문제가 생길 자리가 없다([contexts/media/01-rules.md](../contexts/media/01-rules.md)의 "주소는 처음부터 바뀌지 않는다"). 레거시의 동작은 여전히 재현하지 않았다 |
