# 컨텍스트 간 통합

[strategic/04-context-map.md](../strategic/04-context-map.md)의 관계를 코드로 옮기는 규칙이다.
패키지 이름은 현재 `core` 코드를 따른다(`application/provided`, `application/required`, `adapter/required`).

## 호출 규칙

1. 상대 컨텍스트의 `application/provided`에 있는 인바운드 포트만 호출한다. 상대의 `domain`, 서비스 구현, 어댑터, 리포지토리를 참조하지 않는다.
2. 호출하는 쪽은 자기 `application/required`에 자기 언어로 아웃바운드 포트를 정의한다.
3. 그 포트를 `adapter/required/<상대 컨텍스트>/`에서 상대의 provided 포트로 구현한다. 상대 컨텍스트를 참조하는 곳은 이 어댑터 하나다(부패 방지 계층).
4. 타입 변환은 어댑터에서 한다. 자기 `domain`과 `application`에 상대 컨텍스트의 타입이 나타나지 않는다.
5. 화면에 보여주기 위한 조합은 `app` 모듈에서 한다. `core`의 컨텍스트는 표시 목적만으로 다른 컨텍스트를 호출하지 않는다.

## 허용하는 의존

컨텍스트 맵의 실선 방향과 같다. 표에 없는 방향은 금지다.

| 컨텍스트 | 호출해도 되는 상대 (provided 포트) | 받아도 되는 이벤트 |
|---|---|---|
| member | 없음 | 없음 |
| publishing | member, media | 없음 |
| interaction | member, publishing | publishing |
| media | 없음 | 없음 |
| analytics | 없음 | 없음 |
| notification | interaction | publishing, interaction |

모든 컨텍스트는 `core.shared`(공유 커널)를 쓸 수 있다.

## 예시: 출판이 계정 상태를 확인한다

출판의 규칙("정지된 계정은 글을 쓸 수 없다")에 필요하므로 컨텍스트 간 호출이다.

```kotlin
// core/publishing/application/required/BloggerStatusPort.kt
// 출판의 언어로 정의한다. member 패키지를 모른다.
interface BloggerStatusPort {
    fun canPublish(bloggerId: UUID): Boolean
}

// core/publishing/adapter/required/member/MemberBloggerStatusAdapter.kt
// 출판에서 member를 참조하는 유일한 지점.
@Component
class MemberBloggerStatusAdapter(
    private val getAccountStatus: GetAccountStatusQuery,   // member의 provided 포트
) : BloggerStatusPort {
    override fun canPublish(bloggerId: UUID): Boolean =
        getAccountStatus.get(bloggerId).isActive
}
```

회원을 별도 서비스로 분리하면 이 어댑터 내부만 HTTP 호출로 바꾼다. 호출부는 바뀌지 않는다.

## 예시: 글 상세를 조합한다

작성자 이름은 출판의 규칙에 쓰이지 않으므로 출판이 가져오지 않는다. `app/api`가 조합한다.

```kotlin
// app/api/.../publishing/PostDetailQueryService.kt
@Component
class PostDetailQueryService(
    private val getPost: GetPublishedPostQuery,       // publishing
    private val getProfiles: GetProfilesQuery,        // member
    private val getViewCounts: GetViewCountsQuery,    // analytics
    private val getLikeCounts: GetLikeCountsQuery,    // interaction
) {
    fun get(blogSlug: String, postSlug: String): PostDetailResponse {
        val post = getPost.get(blogSlug, postSlug)
        val author = getProfiles.getAll(setOf(post.ownerId)).getValue(post.ownerId)
        val views = getViewCounts.getAll(setOf(post.id))[post.id] ?: 0
        val likes = getLikeCounts.getAll(setOf(post.id))[post.id] ?: 0
        return PostDetailResponse.of(post, author, views, likes)
    }
}
```

조합 결과의 응답 타입은 `app` 모듈에 둔다. `core`의 어떤 컨텍스트에도 넣지 않는다.

## 이벤트

| 항목 | 규칙 |
|---|---|
| 이벤트 클래스 위치 | 발행 컨텍스트의 `application/provided/event`. 소비 측은 provided만 참조하므로 규칙 1을 지킨다 |
| 내용 | 식별자와 소비 측이 판단에 필요한 최소한의 값. 엔티티를 담지 않는다 |
| 소비 시점 | 발행한 트랜잭션이 커밋된 뒤 (`@TransactionalEventListener(phase = AFTER_COMMIT)`) |
| 리스너 위치 | 리스너는 인바운드 어댑터다. 이벤트가 발행되는 프로세스의 `app` 모듈에 두고, 소비 컨텍스트의 provided 포트를 호출한다 |
| 실패 처리 | 소비가 실패해도 발행 측 트랜잭션은 되돌리지 않는다 |

```kotlin
// core/publishing/application/provided/event/PostPublished.kt
data class PostPublished(val postId: UUID, val blogId: UUID, val ownerId: UUID)

// app/api/.../notification/PostPublishedListener.kt
@Component
class PostPublishedListener(
    private val notifySubscribers: NotifySubscribersUseCase,   // notification
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: PostPublished) = notifySubscribers.notifyNewPost(event.blogId, event.postId)
}
```

같은 프로세스 안의 이벤트만 다룬다. 프로세스 사이의 이벤트(worker에서 api로 등)는 이 문서의 범위가 아니다.

## 트랜잭션

- 한 트랜잭션은 한 컨텍스트의 쓰기만 담는다.
- 트랜잭션 안에서 다른 컨텍스트를 호출해도 되는 것은 조회뿐이다.
- 예외: 출판에서 미디어로 가는 첨부 승격과 연결. 지금은 글 저장과 같은 트랜잭션에 있다. 외부 저장소 I/O와 DB의 정합성 설계는 따로 정한다([strategic/05-legacy-mapping.md](../strategic/05-legacy-mapping.md)의 "확인하지 못한 것").

## 데이터 참조

컨텍스트마다 DB 스키마가 따로 있다. 다른 컨텍스트의 스키마를 SQL로 읽거나 쓰지 않는다. 다른 컨텍스트의 데이터는 그 컨텍스트의 provided 포트로만 얻는다.
스키마 구성과 강제 장치는 [04-persistence-conventions.md](04-persistence-conventions.md)에 있다.

### JPA 연관관계

컨텍스트를 넘는 연관관계 매핑을 하지 않는다. ID 컬럼으로 참조한다.

```kotlin
// 금지
@ManyToOne
val owner: Account

// 권장
@Column(name = "owner_id")
val ownerId: UUID
```

### 외래키

컨텍스트를 넘는 외래키를 두지 않는다. 외래키는 다른 스키마를 참조하는 결합이다.
`ON DELETE CASCADE`나 `SET NULL`처럼 다른 컨텍스트의 데이터를 바꾸는 동작은 숨은 컨텍스트 간 호출이다.
그런 동작이 필요하면 컨텍스트 맵에 따라 동기 호출이나 이벤트로 드러낸다.

같은 컨텍스트 안의 외래키는 둔다.

같은 컨텍스트 안의 애그리거트끼리도 연관관계 매핑 대신 ID로 참조한다. 애그리거트 하나가 한 트랜잭션의 일관성 단위이기 때문이다.

## 조회 성능

목록 화면의 조합은 N+1을 만든다. provided 조회 포트는 ID 묶음을 받는 형태(`getAll(ids)`)로 만든다.

성능이 실제로 문제가 되면 다음을 검토한다.

- 스냅샷 저장: 쓰는 시점의 값을 읽는 쪽 저장소에 함께 저장
- 읽기 전용 모델 분리: 조회 전용 쿼리를 두어 컨텍스트 규칙을 우회

측정 전에 도입하지 않는다.

## 규칙 강제

"허용하는 의존" 표는 ArchUnit으로 강제한다. 컨텍스트마다 하나씩 작성한다.

```kotlin
@ArchTest
val publishing은_허용된_컨텍스트의_provided만_호출한다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.core.publishing..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.contentria.core.member.domain..",
            "com.contentria.core.member.application.required..",
            "com.contentria.core.member.adapter..",
            "com.contentria.core.media.domain..",
            "com.contentria.core.media.application.required..",
            "com.contentria.core.media.adapter..",
            "com.contentria.core.interaction..",
            "com.contentria.core.analytics..",
            "com.contentria.core.notification..",
        )
```
