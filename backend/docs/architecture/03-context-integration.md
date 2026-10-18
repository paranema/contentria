# 컨텍스트 간 통합

[strategic/04-context-map.md](../strategic/04-context-map.md)의 관계를 코드로 옮기는 규칙이다.
모듈러 모놀리스로 운영하지만, 컨텍스트 사이의 상호작용은 서비스로 분리된 것처럼 다룬다. 분리할 때 바꿀 곳을 번역 계층 하나로 줄이기 위해서다.

## 원칙

| 상호작용 | 방식 | 모놀리스에서 | 분리 후 |
|---|---|---|---|
| 조회 | 상대 `-api`의 조회 포트를 호출한다 | 메모리 내 메서드 호출 | HTTP 호출 (번역 계층만 교체) |
| 다른 컨텍스트에 후속 처리를 맡김 | 도메인 이벤트 | 아웃박스 → Kafka → 인박스 | 같음 |
| 다른 컨텍스트의 쓰기를 기다려야 함 | 동기 호출. 컨텍스트 맵의 "동기 호출" 표에 있는 것만 | 메모리 내 메서드 호출 | HTTP 호출 |

## 호출 규칙

1. 상대 컨텍스트의 `-api` 모듈에 있는 포트만 호출한다. 상대의 `-core`는 의존성에 없으므로 참조할 수 없다([01-module-structure.md](01-module-structure.md)).
2. 상대를 호출하는 코드는 번역 계층 한 곳에 모은다. 헥사고날은 `adapter/required/<상대>/`, 레이어드는 `client/`다([02-internal-architecture.md](02-internal-architecture.md)).
3. 번역 계층은 상대의 타입을 자기 타입으로 바꿔서 돌려준다. 번역 계층 밖에 상대 컨텍스트의 타입이 나타나지 않는다.
4. 화면에 보여주기 위한 조합은 `apps/*`의 `composition`에서 한다. 컨텍스트는 표시 목적만으로 다른 컨텍스트를 호출하지 않는다.

## 허용하는 의존

컨텍스트 맵의 방향과 같다. 표에 없는 방향은 금지다. 각 `-core`의 `build.gradle.kts` 의존성이 이 표와 같아야 한다.

| 컨텍스트 | 호출해도 되는 상대 (`-api`) | 소비해도 되는 이벤트 |
|---|---|---|
| member | 없음 | 없음 |
| publishing | member, media | 없음 |
| interaction | member, publishing | publishing |
| media | 없음 | 없음 |
| analytics | 없음 | 없음 |
| notification | interaction | publishing, interaction |

이벤트를 소비하려면 발행 측 `-api`의 이벤트 타입이 필요하다. 그래서 소비만 하는 경우에도 상대 `-api`를 의존성에 넣는다.

## 조회는 원격 호출처럼 다룬다

같은 JVM 안에서는 메서드 호출로 조회한다. 자기 자신에게 HTTP를 보내지 않는다.
대신 나중에 네트워크 호출로 바꿔도 동작이 달라지지 않도록 다음을 지킨다.

| 규칙 | 이유 |
|---|---|
| ID 묶음으로 한 번에 조회한다 (`getAll(ids)`) | 원격이 되면 건마다 호출하는 비용이 그대로 네트워크 왕복이 된다 |
| 엔티티가 아니라 `-api`의 DTO를 주고받는다 | 지연 로딩 프록시는 네트워크를 건너갈 수 없다 |
| 자기 쓰기 트랜잭션을 열기 **전에** 조회한다 | 같은 트랜잭션 안에서 조회하면 상대가 호출자의 커밋 전 데이터를 볼 수 있다. 분리하면 달라지는 동작이다. 분리 후에는 DB 커넥션을 쥔 채 네트워크를 기다리게 된다 |
| 조회 포트 구현은 자기 읽기 전용 트랜잭션에서 동작한다 | 호출자의 트랜잭션에 기대지 않는다 |
| 상대의 예외 타입을 번역 계층 밖으로 내보내지 않는다 | 번역 계층에서 자기 언어의 결과나 오류로 바꾼다 |
| 상대가 응답하지 않을 때의 동작을 정해 둔다 | 지금은 일어나지 않지만 분리 후에는 시간 초과가 생긴다. 조회 실패 시 거부할지, 기본값으로 진행할지를 유스케이스마다 정한다 |

### 예시: 출판이 계정 상태를 확인한다 (헥사고날)

```kotlin
// publishing-core: application/required/BloggerStatusPort.kt
// 출판의 언어로 정의한다. member를 모른다.
interface BloggerStatusPort {
    fun canPublish(bloggerId: UUID): Boolean
}

// publishing-core: adapter/required/member/MemberBloggerStatusAdapter.kt
// 출판에서 member를 참조하는 유일한 지점.
@Component
class MemberBloggerStatusAdapter(
    private val getAccountStatus: GetAccountStatusQuery,   // member-api
) : BloggerStatusPort {
    override fun canPublish(bloggerId: UUID): Boolean =
        getAccountStatus.get(bloggerId).isActive
}

// publishing-core: application/service/PostCommandService.kt
class PostCommandService(
    private val bloggerStatus: BloggerStatusPort,
    private val postRepository: PostRepository,
    private val transactionTemplate: TransactionTemplate,
) : CreatePostUseCase {
    override fun create(command: CreatePostCommand): PostId {
        // 다른 컨텍스트 조회는 트랜잭션 밖에서 먼저 한다
        if (!bloggerStatus.canPublish(command.bloggerId)) throw BloggerNotActive(command.bloggerId)

        return transactionTemplate.execute { postRepository.save(Post.create(command)).id }!!
    }
}
```

회원을 별도 서비스로 분리하면 `MemberBloggerStatusAdapter` 내부만 HTTP 호출로 바꾼다.

### 예시: 글 상세를 조합한다 (apps/api)

```kotlin
// apps/api: composition/PostDetailQuery.kt
@Component
class PostDetailQuery(
    private val getPost: GetPublishedPostQuery,       // publishing-api
    private val getProfiles: GetProfilesQuery,        // member-api
    private val getViewCounts: GetViewCountsQuery,    // analytics-api
    private val getLikeCounts: GetLikeCountsQuery,    // interaction-api
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

## 이벤트

컨텍스트 사이의 이벤트는 모놀리스 안에서도 Kafka로 주고받는다. 서비스로 분리할 때 이벤트 경로를 바꾸지 않기 위해서다.

### 전달 경로

```
[발행 컨텍스트의 트랜잭션]
  애그리거트 저장 + 자기 스키마의 outbox_event에 기록        한 트랜잭션
                       │
                       ▼
[릴레이 (platform/messaging)]
  아직 보내지 않은 outbox_event를 읽어 Kafka로 보낸다
  Kafka가 받았다고 응답하면 보낸 시각을 기록한다
                       │
                       ▼
[소비 컨텍스트의 트랜잭션]
  inbox_event에 (eventId, 소비자)를 기록 + 처리               한 트랜잭션
  이미 기록되어 있으면 처리하지 않는다
```

전달 보증은 "최소 한 번"이다. 릴레이가 보낸 뒤 기록하기 전에 죽으면 같은 이벤트를 다시 보낸다. 중복은 인박스가 거른다.

### 아웃박스

| 항목 | 규칙 |
|---|---|
| 위치 | 발행 컨텍스트 스키마의 `outbox_event` 테이블 ([04-persistence-conventions.md](04-persistence-conventions.md)) |
| 기록 | 애그리거트를 저장하는 트랜잭션 안에서 기록한다. Kafka에 직접 보내지 않는다 |
| 기록하는 코드 | 헥사고날은 `application/required`의 이벤트 발행 포트를 `adapter/required/messaging`이 구현한다. 레이어드는 서비스가 `platform/messaging`의 기록기를 직접 쓴다 |

```kotlin
// publishing-core: application/service/PostCommandService.kt
@Transactional
fun publish(command: PublishPostCommand) {
    val post = postRepository.get(command.postId)
    post.publish()
    postRepository.save(post)
    events.publish(PostPublished(post.id, post.blogId, post.ownerId))   // outbox_event에 기록. 같은 트랜잭션
}
```

### 릴레이

| 항목 | 규칙 |
|---|---|
| 위치 | `platform/messaging`. 발행 컨텍스트를 띄우는 프로세스(현재 `apps/api`)에서 돈다 |
| 동작 | 보낸 시각이 비어 있는 행을 발생 순서대로 읽어 Kafka로 보낸다 |
| 실행 개수 | 프로세스 레플리카가 여럿이어도 릴레이는 하나만 돈다. 여럿이 돌면 같은 애그리거트의 이벤트 순서가 뒤바뀔 수 있다 |
| 보낸 행 정리 | 보존 기간이 지나면 지운다. 기간은 구현 시 정한다 |

나중에 릴레이를 DB 변경 로그 기반 도구(CDC)로 바꿀 수 있다. 발행 측과 소비 측 코드는 바뀌지 않는다.

### 토픽

| 항목 | 규칙 | 예 |
|---|---|---|
| 이름 | `<컨텍스트>.<애그리거트>` | `publishing.post`, `interaction.comment` |
| 한 토픽의 이벤트 | 같은 애그리거트의 이벤트 여러 종류 | `publishing.post`에 PostPublished, PostDeleted |
| 메시지 키 | 애그리거트 ID | 같은 글의 이벤트는 같은 파티션으로 가서 순서가 지켜진다 |
| 이름 상수 | 발행 측 `-api`의 `event/<컨텍스트>Topics.kt` | `PublishingTopics.POST` |

이벤트 종류마다 토픽을 나누지 않는다. 나누면 같은 글의 "발행"과 "삭제"가 다른 토픽으로 가서 순서를 보장할 수 없다.

### 메시지 형식

봉투는 `shared-kernel`에 있다. 본문(payload)은 발행 측 `-api`의 이벤트 클래스다.

```json
{
  "eventId": "0192f3a1-7c2e-7b10-9a4e-2d6f1c8b5e01",
  "type": "PostPublished",
  "version": 1,
  "occurredAt": "2026-10-18T03:00:00Z",
  "aggregateId": "0192f3a0-1111-7000-8000-000000000001",
  "payload": { "postId": "0192f3a0-1111-7000-8000-000000000001", "blogId": "0192f39f-2222-7000-8000-000000000002", "ownerId": "0192f39e-3333-7000-8000-000000000003" }
}
```

| 항목 | 규칙 |
|---|---|
| 직렬화 | JSON |
| 본문 | 식별자와 소비 측이 판단에 필요한 최소한의 값. 엔티티를 담지 않는다 |
| 호환되는 변경 (필드 추가) | 같은 버전을 유지한다. 소비자는 모르는 필드를 무시한다 |
| 호환되지 않는 변경 (필드 삭제, 의미 변경) | 버전을 올린다. 모든 소비자가 옮겨갈 때까지 두 버전을 함께 발행한다 |
| 스키마 레지스트리 | 지금은 쓰지 않는다. 컨텍스트를 서비스로 분리할 때 검토한다 |

### 소비자와 인박스

| 항목 | 규칙 | 예 |
|---|---|---|
| 위치 | 소비 컨텍스트의 진입점. 헥사고날은 `adapter/provided/messaging`, 레이어드는 `messaging` | |
| 소비자 그룹 | `<소비 컨텍스트>.<소비자 이름>` | `interaction.post-deleted` |
| 중복 제거 | 처리와 같은 트랜잭션에서 소비 컨텍스트 스키마의 `inbox_event`에 `(eventId, 소비자)`를 기록한다. 이미 있으면 처리하지 않고 넘어간다 | |
| 오프셋 커밋 | 처리 트랜잭션이 커밋된 뒤 | |
| 실패 | 간격을 늘려 가며 재시도한 뒤 DLT(dead letter topic)로 보낸다. 메시지 하나 때문에 파티션이 막히지 않게 한다 | |
| 거르기 | 같은 토픽의 다른 이벤트 종류는 `type`을 보고 무시한다 | |

```kotlin
// interaction-core: messaging/PostDeletedConsumer.kt (레이어드)
@Component
class PostDeletedConsumer(
    private val inbox: Inbox,                                   // platform/messaging
    private val cleanup: PostInteractionCleanupService,
) {
    @KafkaListener(topics = [PublishingTopics.POST], groupId = "interaction.post-deleted")
    fun on(envelope: EventEnvelope) {
        if (envelope.type != PostDeleted.TYPE) return
        inbox.processOnce(envelope.eventId, consumer = "interaction.post-deleted") {
            cleanup.removeAllFor(envelope.payloadAs<PostDeleted>().postId)  // inbox 기록과 같은 트랜잭션
        }
    }
}
```

### Kafka를 거치지 않는 것

| 경우 | 처리 |
|---|---|
| 같은 컨텍스트 안의 후속 처리 | 같은 트랜잭션에서 처리하거나, 그 컨텍스트 안에서 Spring 이벤트로 처리한다 |
| 미디어의 영상 변환 요청 (Cloudflare Queue) | 미디어 컨텍스트 내부의 외부 연동이다. 컨텍스트 간 이벤트가 아니므로 이 절의 대상이 아니다 |

## 트랜잭션

**한 트랜잭션은 한 컨텍스트의 쓰기만 담는다.**
1단계에서는 DataSource가 하나라서 여러 컨텍스트를 한 트랜잭션에 묶을 수 있다. 하지만 묶지 않는다. 묶으면 코드가 같은 트랜잭션에 조용히 기대게 되고, 서비스로 분리하는 순간 깨진다.

### 즉시 일관성이 필요해 보일 때의 판단 순서

| 순서 | 질문 | 그렇다면 |
|---|---|---|
| 1 | 정말 즉시여야 하는가 | 대부분은 몇 초의 지연을 허용한다. 이벤트로 처리한다 |
| 2 | 두 컨텍스트에 걸친 불변식인가 | 경계가 잘못 그어졌다는 신호다. 데이터나 규칙을 한 컨텍스트로 옮긴다 |
| 3 | 쓰기 전에 다른 컨텍스트의 상태를 확인하는 것인가 | 트랜잭션 밖에서 동기 조회로 확인한 뒤 자기 트랜잭션에서 쓴다 |
| 4 | 여러 컨텍스트의 쓰기를 조율해야 하는가 | 사가로 처리한다 |

3번에는 확인과 쓰기 사이에 상대의 상태가 바뀔 수 있는 시간이 있다. 예를 들어 계정 상태를 확인한 직후 계정이 정지되면 글 하나가 저장될 수 있다. 이 정도의 어긋남은 받아들이고, 필요하면 이벤트로 사후 처리한다.

### 사가

- 단계마다 자기 컨텍스트의 트랜잭션에서 실행한다.
- 실패하면 앞 단계를 보상하거나, 실패해도 데이터가 망가지지 않고 정리 가능한 상태만 남도록 단계 순서를 짠다.
- 2단계 커밋(XA)은 쓰지 않는다.

### 예: 글 저장과 첨부 승격 (제안. 출판을 옮길 때 확정한다)

레거시는 글 저장 트랜잭션 안에서 미디어 승격(외부 저장소의 파일 이동)을 한다([strategic/05-legacy-mapping.md](../strategic/05-legacy-mapping.md)). 새 구조에서는 다음 순서로 나눈다.

```
1. 미디어: 승격 (미디어 트랜잭션. 파일 이동과 미디어 기록 갱신) → 새 주소를 돌려준다
2. 출판:   새 주소로 글을 저장한다 (출판 트랜잭션)
3. 미디어: 파일을 글에 연결한다 (미디어 트랜잭션)

2나 3이 실패하면 승격되었지만 어떤 글에도 연결되지 않은 파일이 남는다.
미디어의 정리 작업이 연결되지 않은 파일을 회수한다.
```

실패가 데이터를 망가뜨리는 대신 치우면 되는 것을 남기도록 순서를 짠다. 보상 로직이 필요 없다.

## 데이터 참조

컨텍스트마다 DB 스키마가 따로 있다. 다른 컨텍스트의 스키마를 SQL로 읽거나 쓰지 않는다. 다른 컨텍스트의 데이터는 그 컨텍스트의 `-api` 포트로만 얻는다.
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

같은 컨텍스트 안의 애그리거트끼리도 연관관계 매핑 대신 ID로 참조한다. 애그리거트 하나가 한 트랜잭션의 일관성 단위이기 때문이다.

### 외래키

컨텍스트를 넘는 외래키를 두지 않는다. 외래키는 다른 스키마를 참조하는 결합이다.
`ON DELETE CASCADE`나 `SET NULL`처럼 다른 컨텍스트의 데이터를 바꾸는 동작은 숨은 컨텍스트 간 호출이다.
그런 동작이 필요하면 컨텍스트 맵에 따라 동기 호출이나 이벤트로 드러낸다.

같은 컨텍스트 안의 외래키는 둔다.

## 조회 성능

목록 화면의 조합은 N+1을 만든다. 조회 포트는 ID 묶음을 받는 형태(`getAll(ids)`)로 만든다.

성능이 실제로 문제가 되면 다음을 검토한다.

- 데이터 복제(로컬 읽기 모델): 다른 컨텍스트의 이벤트를 받아 필요한 값을 자기 저장소에 복사해 둔다
- 읽기 전용 모델 분리: 조회 전용 쿼리를 두어 컨텍스트 규칙을 우회

측정 전에 도입하지 않는다.

## 규칙 강제

| 규칙 | 장치 |
|---|---|
| "허용하는 의존" 표 | 각 `-core`의 Gradle 의존성. 표에 없는 `-api`는 의존성에 넣지 않는다 |
| 다른 컨텍스트의 `-core` 참조 금지 | Gradle 의존성. `-core` 사이에는 의존성이 없다 |
| 번역 계층 밖에서 상대 타입 사용 금지 | ArchUnit ([02-internal-architecture.md](02-internal-architecture.md)) |
| 한 트랜잭션에 여러 컨텍스트의 쓰기 금지 | 코드 리뷰. 2단계에서 트랜잭션 매니저 분리로 강제된다 ([04-persistence-conventions.md](04-persistence-conventions.md)) |

## 정하지 않은 것

| 항목 | 정할 시점 |
|---|---|
| Kafka 클러스터 구성 (배포 방식, 브로커 수, 파티션 수, 보존 기간) | Kafka를 클러스터에 올릴 때. `infra/gitops/02-platform`에 둔다 |
| 로컬 개발 환경의 Kafka | 첫 이벤트를 구현할 때 |
| 아웃박스 보존 기간, 릴레이 주기 | 릴레이를 구현할 때 |
| DLT에 쌓인 메시지를 다시 처리하는 절차 | 첫 소비자를 운영에 올릴 때. `operations/`에 둔다 |
