# 레거시 대응

레거시 모듈(`blog-*`)과 이전 중인 `core`의 코드를 [03-bounded-contexts.md](03-bounded-contexts.md)의 컨텍스트에 대응시킨다.

**임시 문서다.** `blog-*` 모듈의 이전이 끝나면 삭제한다.
이 폴더에서 코드 경로를 쓰는 유일한 문서다.

## 패키지 대응

| 레거시 위치 | 컨텍스트 | 비고 |
|---|---|---|
| `blog-api` `user` | 회원 | `User`는 Account와 Profile로 나뉜다 |
| `blog-api` `auth` (application, domain, infrastructure) | 회원 | |
| `blog-api` `auth/security`, `global/security` | 진입점 (`app/api`) | 요청 인증 처리 |
| `blog-api` `blog`, `category`, `post` | 출판 | |
| `blog-api` `media`, `video` | 미디어 | |
| `blog-worker` 전체 | 미디어 | 영상 변환 진입점 |
| `blog-batch` `jobs/video` | 미디어 | 영상 정리 진입점 |
| `blog-api` `analytics` | 분석 | |
| `blog-common` `common/domain/analytics` | 분석 | 공용 라이브러리에 도메인이 들어가 있다 |
| `blog-batch` `jobs/statistics` | 분석 | 일별 집계 진입점 |
| `blog-batch` `jobs/auth` | 회원 | 만료 토큰 정리 진입점 |
| `blog-api` `dashboard` | 진입점 (`app/api`) | 조회 조합 |
| `blog-api` `comment`, `subscription` | 상호작용 | 코드 전체가 주석 처리되어 있다 |
| `blog-api` `notification` | 알림 | 코드 전체가 주석 처리되어 있다 |
| `blog-common` `global` (error, model, config, aop) | 공유 커널 또는 진입점 설정 | 성격별로 나눈다 |
| `blog-common` `infrastructure/email` | 정하지 않음 | 회원의 출력 포트로 둘지 알림으로 보낼지는 03의 "정하지 않은 것"에 따른다 |
| `core` `account`, `profile` | 회원 | `core.member` 하나로 합친다 |
| `core` `blog`, `post` | 출판 | 빈 디렉터리. `core.publishing`으로 만든다 |

## 레거시에서 확인한 경계 문제

03의 "결정과 이유"가 근거로 삼은 현상이다. 줄 번호는 2026-10-17 기준이다.

### 출판 안의 순환과 공유 트랜잭션

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| blog, post, category가 서로의 서비스를 호출한다 | [BlogFacade.kt:6-8](../../blog-api/src/main/kotlin/com/contentria/api/blog/application/BlogFacade.kt#L6), [PostFacade.kt:4-5](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L4), [CategoryFacade.kt:3](../../blog-api/src/main/kotlin/com/contentria/api/category/application/CategoryFacade.kt#L3) | 출판 컨텍스트 내부 호출이 된다 |
| 블로그 생성이 블로그, 카테고리, 글을 한 트랜잭션에서 만든다 | [BlogFacade.kt:49-67](../../blog-api/src/main/kotlin/com/contentria/api/blog/application/BlogFacade.kt#L49) | 출판 내부 트랜잭션. 정상이다 |
| 가입이 User와 Credential을 한 트랜잭션에서 만든다 | [AuthFacade.kt:23-36](../../blog-api/src/main/kotlin/com/contentria/api/auth/application/AuthFacade.kt#L23) | 회원 내부 트랜잭션. 정상이다 |

### 작성자 개념

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 글 상세의 작성자는 블로그 소유자로 조회한다 | [PostFacade.kt:44](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L44) | 작성자는 블로그 소유자로 확정 |
| 글 수정 권한은 블로그 소유자로, 삭제 권한은 `Post.authorId`로 검사한다 | [PostFacade.kt:131](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L131), [PostFacade.kt:158](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L158), [PostService.kt:95-101](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostService.kt#L95) | 블로그 소유권 하나로 통일. `Post.authorId` 제거 |
| 글 상세가 작성자를 "활성 회원"으로 조회하므로 활성이 아닌 회원의 글은 열리지 않는다 | [PostFacade.kt:44](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L44), [UserService.kt:21-28](../../blog-api/src/main/kotlin/com/contentria/api/user/application/UserService.kt#L21) | 글 상세는 프로필만 조회한다. 노출 여부는 03의 "정하지 않은 것"에서 정한다 |

### 회원 모델의 확산

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 이메일과 권한을 담은 `UserInfo`가 글, 블로그 응답 모델에 그대로 들어간다 | [UserInfo.kt:6-12](../../blog-api/src/main/kotlin/com/contentria/api/user/application/dto/UserInfo.kt#L6), [PostDetailInfo.kt:3](../../blog-api/src/main/kotlin/com/contentria/api/post/application/dto/PostDetailInfo.kt#L3), [BlogLayoutInfo.kt:4](../../blog-api/src/main/kotlin/com/contentria/api/blog/application/dto/BlogLayoutInfo.kt#L4) | 화면은 진입점에서 프로필만 조합한다 |
| auth의 응답 DTO가 user의 응답 DTO를 가져다 쓴다 | [LoginResponse.kt:4](../../blog-api/src/main/kotlin/com/contentria/api/auth/controller/dto/LoginResponse.kt#L4) | 회원 하나로 합쳐지며 해소 |
| auth의 `Credential`이 user의 `AuthProvider`를 쓴다 | [Credential.kt:3](../../blog-api/src/main/kotlin/com/contentria/api/auth/domain/Credential.kt#L3) | 회원 하나로 합쳐지며 해소 |

### 조회수와 좋아요 수

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| `Post`에 `likeCount`, `viewCount`가 있지만 값을 증가시키는 코드가 없다 | [Post.kt:43-45](../../blog-api/src/main/kotlin/com/contentria/api/post/domain/Post.kt#L43) | 두 필드를 글에서 제거 |
| 화면은 증가하지 않는 `likeCount`를 표시한다 | [PostCard.tsx:69](../../../frontend/src/components/blog/PostCard.tsx#L69) | 상호작용의 좋아요 수로 교체 |
| 인기 글 쿼리가 posts 테이블을 직접 JOIN해 제목과 슬러그를 가져온다 | [DailyStatisticsJpaRepository.kt:76](../../blog-common/src/main/kotlin/com/contentria/common/domain/analytics/infrastructure/DailyStatisticsJpaRepository.kt#L76) | 분석은 글 ID만 반환. 제목은 블로그 대시보드 조합에서 붙인다 |
| 분석 엔티티가 공용 라이브러리에 있다 | [VisitLog.kt](../../blog-common/src/main/kotlin/com/contentria/common/domain/analytics/VisitLog.kt) | 분석 컨텍스트로 이동 |

### 미디어

| 현상 | 근거 | 새 구조에서 |
|---|---|---|
| 미디어가 마크다운 이미지 문법을 직접 파싱한다 | [MediaService.kt:33](../../blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L33), [MediaService.kt:96](../../blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L96) | 출판이 참조 파일 목록을 뽑아 넘긴다 |
| 영상 상태 전이가 세 모듈에 SQL 문자열로 흩어져 있다 | [Video.kt](../../blog-api/src/main/kotlin/com/contentria/api/video/domain/Video.kt), [VideoJobRepository.kt:25-63](../../blog-worker/src/main/kotlin/com/contentria/worker/video/VideoJobRepository.kt#L25), [VideoGcJobConfig.kt:66-111](../../blog-batch/src/main/kotlin/com/contentria/batch/jobs/video/VideoGcJobConfig.kt#L66) | 영상 모델 하나에 상태 규칙을 둔다. worker와 batch는 그 모델의 진입점이 된다 |
| 글 저장 트랜잭션 안에서 외부 저장소의 파일을 옮긴다 | [PostFacade.kt:107](../../blog-api/src/main/kotlin/com/contentria/api/post/application/PostFacade.kt#L107), [MediaService.kt:95-130](../../blog-api/src/main/kotlin/com/contentria/api/media/application/MediaService.kt#L95) | 아래 "확인하지 못한 것" 참고 |

## 확인하지 못한 것

| 항목 | 확인 방법 |
|---|---|
| 글 저장이 승격 뒤에 실패하면, DB의 미디어 기록은 롤백되지만 외부 저장소에서 이미 옮긴 파일은 되돌아가지 않을 것으로 보인다. 코드를 읽고 추론한 것이며 재현하지 않았다 | 승격 후 글 저장에서 예외를 내는 테스트로 재현 |
| 스키마 이전 계획 (users를 accounts와 profiles로 나누는 순서, `posts.author_id` 제거 시점) | 이 문서의 범위가 아니다. 이전 작업 계획에서 정한다 |
