# 컨텍스트 내부 구조

`<컨텍스트>-core` 모듈 안의 구조는 컨텍스트마다 고른다. 바깥 계약은 모든 컨텍스트가 같다.
모듈 배치는 [01-module-structure.md](01-module-structure.md), 컨텍스트 사이의 규칙은 [03-context-integration.md](03-context-integration.md)에 있다.

## 고정하는 것과 고르는 것

| 모든 컨텍스트에 고정 | 컨텍스트마다 고름 |
|---|---|
| `<컨텍스트>-api` 모듈의 모양 (조회 포트, DTO, 이벤트) | `-core` 내부 구조 (헥사고날, 레이어드) |
| 자기 스키마만 접근, 컨텍스트별 마이그레이션 | 도메인 모델의 풍부함 |
| 아웃박스로 이벤트 발행, 인박스로 중복 수신 방지 | 포트 인터페이스를 둘지, 서비스가 리포지토리를 바로 쓸지 |
| 다른 컨텍스트 호출은 번역 계층 한 곳에서만 | 패키지 이름 (아래 규칙 안에서) |
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

## 헥사고날

### 패키지

```
com.contentria.<컨텍스트>/
├── domain/                      애그리거트, 값 객체, 도메인 규칙
├── application/
│   ├── provided/                이 컨텍스트의 진입점이 호출하는 유스케이스 인터페이스
│   ├── required/                이 컨텍스트가 필요로 하는 기능의 인터페이스 (저장, 외부 시스템, 다른 컨텍스트)
│   └── service/                 유스케이스 구현. <컨텍스트>-api의 조회 포트 구현
├── adapter/
│   ├── provided/                들어오는 쪽
│   │   ├── web/                 HTTP 컨트롤러
│   │   ├── messaging/           Kafka 소비자
│   │   ├── batch/               배치 잡
│   │   └── queue/               외부 큐 소비자 (media만)
│   └── required/                나가는 쪽. application/required의 구현
│       ├── persistence/         JPA 리포지토리
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
2. `application`은 `domain`, 자기 `-api`의 타입에만 의존한다.
3. `adapter/required`는 `application/required`의 인터페이스를 구현한다.
4. `adapter/provided`는 `application/provided`나 자기 `-api`의 포트만 호출한다. `service` 구현체를 직접 주입받지 않는다.

### 포트 이름

기술 용어가 아니라 유스케이스 언어를 쓴다.

```kotlin
// 권장
interface PostRepository          // 저장은 Repository로 통일한다
interface BloggerStatusPort       // 다른 컨텍스트에서 얻는 것
interface PasswordHasher          // 외부 기능

// 유스케이스는 동사로 시작한다
interface CreatePostUseCase
interface GetProfilesQuery        // 조회는 Query 접미사
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
├── web/                         HTTP 컨트롤러
├── messaging/                   Kafka 소비자
├── batch/                       배치 잡
├── service/                     비즈니스 로직, 트랜잭션 경계. <컨텍스트>-api의 조회 포트 구현
├── domain/                      엔티티, Spring Data 리포지토리 인터페이스
├── client/                      번역 계층. 다른 컨텍스트의 -api를 호출한다
├── external/                    외부 시스템 연동 (메일 발송 등)
└── config/                      채널별 설정 클래스
```

예시 (상호작용):

```
com.contentria.interaction/
├── web/          SubscriptionController, CommentController, LikeController
├── messaging/    PostDeletedConsumer
├── service/      SubscriptionService, CommentService, LikeService
├── domain/       Subscription, Comment, Like, CommentRepository
├── client/       PublishingClient (publishing-api → 자기 타입), MemberClient
└── config/       InteractionWebConfiguration, InteractionMessagingConfiguration
```

### 의존 방향

```
web, messaging, batch ─→ service ─→ domain
                            │
                            ├─→ client ─→ 다른 컨텍스트의 -api
                            └─→ external
```

1. 진입점(`web`, `messaging`, `batch`)은 `service`만 호출한다. 리포지토리를 직접 쓰지 않는다.
2. `domain`은 `service`, 진입점, `client`를 모른다.
3. 다른 컨텍스트의 `-api`를 호출하는 곳은 `client` 하나다. `client`는 상대의 타입을 자기 타입으로 바꿔서 돌려준다.
4. 포트 인터페이스(`required`)를 두지 않는다. 서비스가 Spring Data 리포지토리를 바로 쓴다.

## ArchUnit

### 모든 컨텍스트

```kotlin
@ArchTest
val 다른_컨텍스트의_api는_번역_계층과_소비자에서만_참조한다: ArchRule =
    noClasses()
        .that().resideInAPackage("com.contentria.interaction..")
        .and().resideOutsideOfPackages(
            "com.contentria.interaction.client..",      // 레이어드의 번역 계층
            "com.contentria.interaction.messaging..",   // 다른 컨텍스트 이벤트의 소비자
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
            "com.contentria.*.service..",
            "com.contentria.*.application..",
            "com.contentria.*.adapter..",
        )
```

다른 컨텍스트의 `-core`에 대한 참조는 Gradle 의존성으로 막으므로 ArchUnit 규칙이 따로 필요 없다.

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
