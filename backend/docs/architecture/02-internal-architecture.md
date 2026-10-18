# 컨텍스트 내부 구조

`<컨텍스트>-core` 모듈 안의 구조는 컨텍스트마다 고른다. 바깥 계약은 모든 컨텍스트가 같다.
모듈 배치는 [01-module-structure.md](01-module-structure.md), 컨텍스트 사이의 규칙은 [03-context-integration.md](03-context-integration.md)에 있다.

## 고정하는 것과 고르는 것

| 모든 컨텍스트에 고정 | 컨텍스트마다 고름 |
|---|---|
| `<컨텍스트>-api` 모듈의 모양 (조회 포트, DTO, 이벤트) | `-core` 내부 구조 (헥사고날, 레이어드) |
| 자기 스키마만 접근, 컨텍스트별 마이그레이션 | 도메인 모델의 풍부함 |
| 아웃박스로 이벤트 발행, 인박스로 중복 수신 방지 | 리포지토리 외의 외부 의존을 역전할지 |
| 리포지토리 규칙 (아래 "리포지토리") | |
| 다른 컨텍스트 호출은 번역 계층 한 곳에서만 | |
| 채널별 설정 클래스 (`config` 패키지) | |

바깥 계약이 고정되어 있으므로, 레이어드로 시작한 컨텍스트가 복잡해지면 내부만 헥사고날로 바꿀 수 있다. 다른 컨텍스트는 영향을 받지 않는다.

## 고르는 기준

| 질문 | 그렇다면 |
|---|---|
| 핵심 서브도메인인가 | 헥사고날 |
| 애그리거트 하나만 보고 판단하는 규칙(불변식)이 많은가 | 헥사고날 |
| 교체할 가능성이 있는 외부 의존이 여럿인가 | 헥사고날 |
| 셋 다 아니다 | 레이어드로 시작한다. 규칙이 늘면 내부만 바꾼다 |

## 컨텍스트별 배정

| 컨텍스트 | 서브도메인 | 내부 구조 | 이유 |
|---|---|---|---|
| member | 일반 | 헥사고날 | 보안 규칙이 많다. 소셜 공급자, 메일 등 외부 연동이 많다 |
| publishing | 핵심 | 헥사고날 | 규칙이 가장 많다(발행 상태, 슬러그, 카테고리 트리, 소유권). 제품의 차별점이다 |
| media | 지원 | 헥사고날 | 규칙은 영상 상태 기계 정도로 작지만 외부 의존(R2, Cloudflare Queue, ffmpeg)이 많고 교체 가능성이 있다 |
| interaction | 지원 | 레이어드 | 규칙이 작다(구독·좋아요 중복 금지, 댓글 작성). 외부 의존은 다른 컨텍스트 두 곳뿐이다 |
| analytics | 지원 | 레이어드 | 쓰기는 기록 추가뿐이고 나머지는 집계 쿼리다. 도메인 모델이 얇다 |
| notification | 일반 | 레이어드 | 이벤트를 받아 저장하고 보내는 흐름이다 |

배정을 바꾸면 이 표와 해당 `contexts/<컨텍스트>/README.md`를 함께 고친다.

## 두 구조의 차이

| | 레이어드 | 헥사고날 |
|---|---|---|
| 리포지토리 인터페이스 위치 | `domain/` | `application/required/` |
| 리포지토리 구현 | Spring Data가 생성. 복잡한 조회만 `infra/persistence/` | Spring Data가 생성. 복잡한 조회만 `adapter/required/persistence/` |
| 리포지토리 외의 외부 의존 (다른 컨텍스트, 메일 등) | application이 `infra/`를 직접 쓴다 | `application/required/`의 포트를 `adapter/required/`가 구현한다 |
| 진입점이 호출하는 것 | application의 서비스 | `application/provided/`의 유스케이스 인터페이스 |

두 구조 모두 리포지토리는 의존을 역전한다. 차이는 리포지토리 외의 외부 의존을 역전하는가다.

## 리포지토리

두 구조에 공통이다.

리포지토리 인터페이스는 Spring Data의 `Repository` 마커를 상속하고, 필요한 메서드만 선언한다. 구현은 Spring Data가 실행 시점에 만든다.
메서드 이름만으로 표현할 수 없는 조회는 fragment 인터페이스로 분리하고, 그 구현만 직접 작성한다.

```kotlin
// 리포지토리: 필요한 메서드만 선언한다
interface CommentRepository : Repository<Comment, UUID>, CommentQueries {
    fun save(comment: Comment): Comment
    fun findById(id: UUID): Comment?
    fun existsByPostIdAndAuthorId(postId: UUID, authorId: UUID): Boolean
}

// fragment: 시그니처는 리포지토리와 같은 계층에 둔다
interface CommentQueries {
    fun findThread(postId: UUID, limit: Int): List<Comment>
}

// fragment 구현: 쿼리 문장은 저장 기술 계층에만 둔다.
// 이름 규칙(<fragment 이름>Impl)으로 Spring Data가 찾아 리포지토리에 붙인다.
class CommentQueriesImpl(private val em: EntityManager) : CommentQueries {
    override fun findThread(postId: UUID, limit: Int): List<Comment> =
        em.createQuery("select c from Comment c where c.postId = :postId order by c.createdAt", Comment::class.java)
            .setParameter("postId", postId)
            .setMaxResults(limit)
            .resultList
}
```

| 규칙 | 이유 |
|---|---|
| `JpaRepository`, `CrudRepository`가 아니라 `Repository`를 상속한다 | 선언하지 않은 메서드(`deleteAll`, `findAll` 등)가 생기지 않게 한다 |
| 리포지토리 인터페이스에 `@Query`, `@Lock`, `@EntityGraph`를 쓰지 않는다. 필요하면 fragment로 옮긴다 | 쿼리 문장과 잠금 방식은 저장 기술의 세부다 |
| `Page`, `Pageable`, `Sort`, `Specification` 같은 Spring Data 타입을 시그니처에 쓰지 않는다 | 기술 의존을 마커 하나로 묶어 둔다 |
| 메서드 이름이 도메인이나 유스케이스의 언어와 맞지 않으면 fragment로 옮긴다 | Spring Data의 이름 규칙에 언어가 끌려가지 않게 한다 |
| 애그리거트 하나에 리포지토리 하나 | 애그리거트 단위로 저장하고 꺼낸다 |

### 이 규칙을 쓰지 않는 경우

| 경우 | 대신 |
|---|---|
| 도메인 모델과 영속성 모델을 따로 둔다 (엔티티가 아닌 도메인 객체를 저장한다) | 리포지토리를 순수 인터페이스로 두고 어댑터에서 매핑과 함께 구현한다 |

Spring Data는 엔티티를 다룬다. 도메인 모델이 곧 JPA 엔티티일 때만 이 규칙이 성립한다.

### 테스트

`Repository` 마커에는 메서드가 없다. 애플리케이션 테스트에서는 리포지토리 인터페이스를 인메모리 가짜 구현으로 바꿔 쓸 수 있다.
fragment의 쿼리는 실제 DB(Testcontainers)로 검증한다.

## 헥사고날

### 패키지

```
com.contentria.<컨텍스트>/
├── domain/                      애그리거트, 값 객체, 도메인 규칙
├── application/
│   ├── provided/                이 컨텍스트의 진입점이 호출하는 유스케이스 인터페이스
│   ├── required/                이 컨텍스트가 필요로 하는 기능의 인터페이스
│   │                            리포지토리(Spring Data 마커), 외부 시스템, 다른 컨텍스트
│   └── service/                 유스케이스 구현. <컨텍스트>-api의 조회 포트 구현
├── adapter/
│   ├── provided/                들어오는 쪽
│   │   ├── web/                 HTTP 컨트롤러
│   │   ├── messaging/           Kafka 소비자
│   │   ├── batch/               배치 잡
│   │   └── queue/               외부 큐 소비자 (media만)
│   └── required/                나가는 쪽. application/required의 구현
│       ├── persistence/         리포지토리 fragment 구현
│       ├── messaging/           아웃박스 기록
│       ├── <상대 컨텍스트>/       번역 계층. 상대의 -api를 호출한다
│       └── <외부 시스템>/         R2, 메일, 소셜 공급자 등
└── config/                      채널별 설정 클래스
```

다른 컨텍스트에 공개하는 조회 포트는 `application/provided`가 아니라 `<컨텍스트>-api`에 있다.
`application/provided`는 자기 컨텍스트의 진입점만 호출하는 유스케이스(주로 명령)다.

### 의존 방향

```
adapter/provided ─→ application/provided ─→ application/service ─→ domain
                                                  │
                                                  └─→ application/required ←─ adapter/required
```

1. `domain`은 아무것도 의존하지 않는다. JPA 애노테이션만 예외로 허용한다.
2. `application`은 `domain`, 자기 `-api`의 타입에 의존한다. Spring Data에서는 `Repository` 마커 하나만 허용하고, 그것도 `application/required`의 리포지토리에서만 쓴다.
3. `adapter/required`는 `application/required`의 인터페이스를 구현한다.
4. `adapter/provided`는 `application/provided`나 자기 `-api`의 포트만 호출한다. `service` 구현체를 직접 주입받지 않는다.

### 포트 이름

기술 용어가 아니라 유스케이스 언어를 쓴다.

```kotlin
// application/required
interface PostRepository : Repository<Post, UUID>   // 저장은 Repository로 통일한다
interface BloggerStatusPort                          // 다른 컨텍스트에서 얻는 것
interface PasswordHasher                             // 외부 기능

// application/provided
interface CreatePostUseCase                          // 유스케이스는 동사로 시작한다
interface GetProfilesQuery                           // 조회는 Query 접미사
```

### 경계에 엔티티를 노출하지 않는다

`application/provided`와 `<컨텍스트>-api`의 시그니처에는 DTO만 쓴다.

```kotlin
// 권장
data class AuthenticatedAccount(val accountId: UUID, val roles: Set<String>)

// 비권장: 엔티티를 그대로 반환
fun authenticate(email: String, password: String): Account
```

엔티티를 반환하면 지연 로딩 프록시가 웹 계층까지 나가고, JSON 직렬화 시점에 컬렉션이 전부 로딩된다.

## 레이어드

### 패키지

```
com.contentria.<컨텍스트>/
├── presentation/
│   ├── web/                     HTTP 컨트롤러
│   ├── messaging/               Kafka 소비자
│   └── batch/                   배치 잡
├── application/                 유스케이스, 트랜잭션 경계. <컨텍스트>-api의 조회 포트 구현
├── domain/                      엔티티, 도메인 규칙, 리포지토리 인터페이스(Spring Data 마커)
├── infra/
│   ├── persistence/             리포지토리 fragment 구현
│   ├── client/                  번역 계층. 다른 컨텍스트의 -api를 호출한다
│   └── external/                외부 시스템 연동 (메일 발송 등)
└── config/                      채널별 설정 클래스
```

예시 (상호작용):

```
com.contentria.interaction/
├── presentation/
│   ├── web/         SubscriptionController, CommentController, LikeController
│   └── messaging/   PostDeletedConsumer
├── application/     SubscriptionService, CommentService, LikeService
├── domain/          Subscription, Comment, Like, CommentRepository, CommentQueries
├── infra/
│   ├── persistence/ CommentQueriesImpl
│   └── client/      PublishingClient (publishing-api → 자기 타입), MemberClient
└── config/          InteractionWebConfiguration, InteractionMessagingConfiguration
```

### 의존 방향

```
presentation ─→ application ─→ domain ←─ infra/persistence
                    │
                    └─→ infra/client, infra/external
```

1. `presentation`은 `application`만 호출한다. `domain`의 리포지토리나 `infra`를 직접 쓰지 않는다.
2. `application`은 `domain`과 `infra`를 쓴다. 리포지토리는 `domain`의 인터페이스로 쓴다.
3. `infra`는 `domain`만 안다. `application`과 `presentation`을 모른다.
4. `domain`은 아무것도 의존하지 않는다. JPA 애노테이션과 Spring Data `Repository` 마커만 예외로 허용한다.
5. 다른 컨텍스트의 `-api`를 호출하는 곳은 `infra/client` 하나다. `client`는 상대의 타입을 자기 타입으로 바꿔서 돌려준다.
6. 리포지토리를 뺀 외부 의존은 포트 인터페이스 없이 `infra`를 직접 쓴다.

## ArchUnit

### 모든 컨텍스트

```kotlin
@ArchTest
val 다른_컨텍스트의_api는_번역_계층과_소비자에서만_참조한다: ArchRule =
    noClasses()
        .that().resideInAPackage("com.contentria.interaction..")
        .and().resideOutsideOfPackages(
            "com.contentria.interaction.infra.client..",               // 레이어드의 번역 계층
            "com.contentria.interaction.presentation.messaging..",     // 다른 컨텍스트 이벤트의 소비자
        )
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.contentria.publishing.api..",
            "com.contentria.member.api..",
        )

@ArchTest
val api_모듈은_Spring과_JPA를_모른다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.*.api..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")

@ArchTest
val app은_api와_설정_클래스만_쓴다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.app..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.contentria.*.domain..",
            "com.contentria.*.application..",
            "com.contentria.*.adapter..",
            "com.contentria.*.infra..",
            "com.contentria.*.presentation..",
        )

@ArchTest
val Spring_Data는_Repository_마커만_쓴다: ArchRule =
    noClasses().that().resideInAnyPackage("com.contentria.*.domain..", "com.contentria.*.application..")
        .should().dependOnClassesThat(
            resideInAPackage("org.springframework.data..")
                .and(not(equivalentTo(org.springframework.data.repository.Repository::class.java)))
        )
```

다른 컨텍스트의 `-core`에 대한 참조는 Gradle 의존성으로 막으므로 ArchUnit 규칙이 따로 필요 없다.

### 레이어드 컨텍스트에만

```kotlin
@ArchTest
val 계층_의존_방향: ArchRule =
    layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Presentation").definedBy("com.contentria.interaction.presentation..")
        .layer("Application").definedBy("com.contentria.interaction.application..")
        .layer("Domain").definedBy("com.contentria.interaction.domain..")
        .layer("Infra").definedBy("com.contentria.interaction.infra..")
        .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
        .whereLayer("Infra").mayOnlyBeAccessedByLayers("Application")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infra", "Presentation")
```

`Presentation`이 `Domain`에 접근하는 것은 엔티티의 식별자 같은 값 타입을 쓰는 경우를 위해 허용한다. 리포지토리 직접 사용은 코드 리뷰로 막는다.

### 헥사고날 컨텍스트에만

```kotlin
@ArchTest
val 도메인은_웹과_저장소_기술을_모른다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.publishing.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.web..",
            "org.springframework.data..",
            "org.springframework.security..",
        )

@ArchTest
val Spring_Data_마커는_리포지토리_포트에서만_쓴다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.publishing.application..")
        .and().resideOutsideOfPackage("com.contentria.publishing.application.required..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")

@ArchTest
val 안쪽은_바깥쪽을_모른다: ArchRule =
    noClasses().that().resideInAnyPackage("com.contentria.publishing.domain..", "com.contentria.publishing.application..")
        .should().dependOnClassesThat().resideInAPackage("com.contentria.publishing.adapter..")

@ArchTest
val 진입점은_서비스_구현체를_모른다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.publishing.adapter.provided..")
        .should().dependOnClassesThat().resideInAPackage("com.contentria.publishing.application.service..")
```

규칙이 컨텍스트마다 반복되므로, 구현할 때 컨텍스트 이름을 받는 공통 규칙 함수로 만든다.

## 결정과 이유

### 모든 컨텍스트에 헥사고날을 쓰지 않는다

**이전 결정:** 모든 컨텍스트에 같은 헥사고날 구조를 적용했다.

**뒤집은 이유:** [strategic/01-domain-overview.md](../strategic/01-domain-overview.md)는 서브도메인 분류에 따라 투자를 달리하기로 했다. 규칙이 작은 지원 컨텍스트에 포트와 어댑터를 모두 두면, 인터페이스와 구현이 일대일로 늘어나기만 하고 보호할 규칙은 없다.
MSA 대비에 필요한 것은 바깥 계약이고, 바깥 계약은 내부 구조와 무관하게 고정했다.

### 리포지토리 인터페이스에 Spring Data `Repository` 마커를 허용한다

| 선택지 | 의존 역전 | 저장소 계층의 기술 의존 | 애그리거트당 파일 |
|---|---|---|---|
| 순수 인터페이스 + 구현 클래스 + Spring Data 인터페이스 (레거시 `blog-api` 방식) | 됨 | 없음 | 3개 |
| **Spring Data `Repository` 마커를 상속한 인터페이스** | 됨 | 마커 하나 | 1개 (복잡한 조회가 있으면 2~3개) |
| 리포지토리를 저장 기술 계층에만 두고 application이 직접 사용 | 안 됨 | 없음 | 1개 |

레거시 방식의 구현 클래스는 대부분 Spring Data 인터페이스에 한 줄씩 위임하는 코드다.
마커를 상속하면 의존 역전과 선언한 메서드만 공개하는 성질은 유지하면서 위임 코드가 사라진다.
대가는 리포지토리가 있는 계층(레이어드의 `domain`, 헥사고날의 `application/required`)이 Spring Data의 마커 인터페이스 하나를 아는 것이다.

헥사고날에서도 같은 규칙을 쓴다. 헥사고날을 고른 이유는 외부 시스템과 도메인 규칙이지 DB 교체 가능성이 아니므로, 리포지토리 포트에 마커가 붙어도 헥사고날을 고른 이유는 그대로 지켜진다.
리포지토리 외의 포트(다른 컨텍스트, 외부 시스템)는 순수 인터페이스와 어댑터로 둔다.

### 레이어드의 진입점과 외부 연동을 `presentation/`, `infra/`로 묶는다

진입점(`web`, `messaging`, `batch`)과 외부 연동(`persistence`, `client`, `external`)을 각각 한 계층으로 묶어, 계층 하나가 패키지 하나가 되게 했다.
널리 알려진 이름이라 설명 없이 읽히고, 헥사고날의 `adapter/provided`, `adapter/required`와 짝이 맞는다.
`presentation`에는 화면이 아닌 진입점(Kafka 소비자, 배치 잡)도 들어간다. DDD 문헌에서 `interfaces`라고 부르는 계층이지만, 더 익숙한 이름을 골랐다.
