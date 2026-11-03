# 컨텍스트 아키텍처

외부에 노출하는 공개 계약(`<컨텍스트>-api`)은 모든 컨텍스트가 동일한 형태를 따르지만, 각 `<컨텍스트>-core` 모듈의 내부 구조는 서브도메인의 복잡도와 외부 연동 특성에 맞춰 **헥사고날 아키텍처** 또는 **레이어드 아키텍처**를 선택합니다([배경 참고](#컨텍스트별-아키텍처-차등-적용)). 외부 계약이 표준화되어 있으므로, 단순한 레이어드 구조로 시작한 컨텍스트라도 추후 비즈니스 규칙이 복잡해지면 다른 컨텍스트에 영향을 주지 않고 내부만 헥사고날 구조로 전환할 수 있습니다.

## 개요

### 선정 기준

컨텍스트의 도메인 규칙 밀도와 외부 인프라 의존성에 따라 다음 기준으로 내부 아키텍처를 선정합니다.

| 판단 질문                                                               | 권장 아키텍처 |
| ----------------------------------------------------------------------- | ------------- |
| 제품의 차별점이 되는 핵심(Core) 서브도메인인가?                         | 헥사고날      |
| 단일 애그리거트 내부에서 검증해야 하는 비즈니스 불변식과 규칙이 많은가? | 헥사고날      |
| 외부 인프라나 서드파티 연동이 많고 향후 교체·모킹 필요성이 높은가?      | 헥사고날      |
| 위 세 가지에 모두 해당하지 않는가?                                      | 레이어드      |

### 컨텍스트별 아키텍처

위 선정 기준에 따라 6개 바운디드 컨텍스트에 배정한 내부 아키텍처입니다. 컨텍스트의 아키텍처 배정을 변경할 때는 이 표와 해당 컨텍스트의 `docs/contexts/<컨텍스트>/README.md`를 함께 수정합니다.

| 컨텍스트       | 서브도메인 분류   | 내부 아키텍처 | 선정 근거                                                                                                            |
| -------------- | ----------------- | ------------- | -------------------------------------------------------------------------------------------------------------------- |
| `member`       | 일반 (Generic)    | 헥사고날      | 인증·토큰·비밀번호 등 보안 규칙이 복잡하며, Google OAuth·SMTP·reCAPTCHA 등 외부 연동 지점이 많습니다.                |
| `publishing`   | 핵심 (Core)       | 헥사고날      | 제품의 핵심 도메인으로 발행 상태, 슬러그 유일성, 카테고리 트리, 소유권 검증 등 비즈니스 규칙이 가장 풍부합니다.      |
| `media`        | 지원 (Supporting) | 헥사고날      | 도메인 모델 자체는 영상 상태 머신 중심으로 간결하지만, Cloudflare R2·Queues·FFmpeg 등 무거운 외부 의존이 많습니다.   |
| `interaction`  | 지원 (Supporting) | 레이어드      | 구독·좋아요 중복 방지와 댓글 작성 등 규칙이 단순하며, 외부 연동도 타 컨텍스트 두 곳(`member`, `publishing`)뿐입니다. |
| `analytics`    | 지원 (Supporting) | 레이어드      | 쓰기 작업은 방문 로그 적재뿐이고 대부분 집계 쿼리 위주여서 도메인 모델이 얇습니다.                                   |
| `notification` | 일반 (Generic)    | 레이어드      | 도메인 이벤트를 수신해 알림 내역을 저장하고 조회하는 직관적인 CRUD 흐름입니다.                                       |

### 구조 비교

두 아키텍처 모두 데이터 저장소(리포지토리)에 대해서는 인터페이스를 통한 의존성 역전(DIP)을 적용합니다. 핵심 차이는 **리포지토리 외의 외부 의존성과 인바운드 유스케이스까지 포트 인터페이스로 추상화하는지 여부**입니다.

| 비교 항목                                       | 헥사고날 (Hexagonal)                                                 | 레이어드 (Layered)                           |
| ----------------------------------------------- | -------------------------------------------------------------------- | -------------------------------------------- |
| 인바운드 진입점이 호출하는 대상                 | `application/provided/`의 유스케이스 인터페이스                      | `application/` 계층의 서비스 클래스          |
| 리포지토리 인터페이스 위치                      | `application/required/`                                              | `domain/`                                    |
| 리포지토리 외 외부 의존 (타 컨텍스트, 외부 API) | `application/required/`에 포트를 정의하고 `adapter/required/`가 구현 | `application/`이 `infra/` 클래스를 직접 호출 |

## 헥사고날 아키텍처

도메인 규칙이 풍부하거나 외부 연동 지점이 많은 컨텍스트(`member`, `publishing`, `media`)는 포트와 어댑터 계층을 두어 비즈니스 로직을 외부 기술로부터 격리합니다.

### 패키지 구조

헥사고날 컨텍스트는 도메인(`domain`), 응용(`application`), 입출력 어댑터(`adapter`), 스프링 설정(`config`) 패키지로 구성됩니다.

```text
com.contentria.<컨텍스트>/
├── domain/                      애그리거트, 엔티티, 값 객체(VO), 도메인 규칙
├── application/
│   ├── provided/                인바운드 진입점이 호출하는 유스케이스 인터페이스 (Command / Query)
│   ├── required/                애플리케이션이 필요로 하는 아웃바운드 포트 인터페이스
│   │                            (리포지토리, 외부 시스템 연동, 타 컨텍스트 조회 포트)
│   └── service/                 유스케이스 구현체 및 <컨텍스트>-api 공개 포트 구현체
├── adapter/
│   ├── provided/                인바운드 어댑터 (진입점)
│   │   ├── web/                 HTTP 컨트롤러
│   │   ├── messaging/           Kafka 이벤트 컨슈머
│   │   ├── batch/               Spring Batch 잡
│   │   └── queue/               외부 큐 폴러 (media 컨텍스트 전용)
│   └── required/                아웃바운드 어댑터 (application/required 포트의 구현체)
│       ├── messaging/           Outbox 이벤트 기록 어댑터
│       ├── <상대 컨텍스트>/       타 컨텍스트의 -api를 호출하는 번역 계층 (ACL)
│       └── <외부 시스템>/         Cloudflare R2, SMTP 메일, OAuth 공급자 연동 등
└── config/                      채널별 Spring @Configuration 설정 클래스
```

- 타 컨텍스트나 화면 조합(`composition`)에 공개하는 인터페이스는 `application/provided`가 아니라 **`<컨텍스트>-api` 모듈**에 둡니다.
- `application/provided`에는 자기 컨텍스트 내부의 인바운드 어댑터(`adapter/provided/*`)가 호출하는 유스케이스만 둡니다.

### 계층 의존 방향

모든 의존성은 바깥쪽 어댑터(`adapter`)에서 안쪽 응용(`application`)과 도메인(`domain`)을 향합니다.

```mermaid
flowchart LR
    AP["adapter/provided"] --> P["application/provided"]
    P --> S["application/service"]
    S --> D["domain"]
    S --> R["application/required"]
    AR["adapter/required"] --> R
```

1. `domain`은 다른 어떤 계층에도 의존하지 않습니다(JPA 매핑 애노테이션과 `shared-kernel`만 예외로 허용).
2. `application`은 `domain`과 자기 컨텍스트의 `-api` 타입에만 의존합니다. Spring Data 의존성은 `application/required`의 리포지토리 인터페이스가 상속하는 `Repository` 마커 하나만 허용합니다.
3. `adapter/required`는 `application/required`에 정의된 아웃바운드 포트를 구현합니다.
4. `adapter/provided`는 `application/provided` 또는 자기 `-api`의 인터페이스만 호출하며, `application/service`의 구현 클래스를 직접 주입받지 않습니다.

### 포트 명명 규칙

인바운드·아웃바운드 포트 인터페이스는 기술 용어가 아닌 도메인 역할과 유스케이스 언어로 명명합니다.

```kotlin
// application/provided (인바운드 포트)
interface CreatePostUseCase                          // 상태 변경 유스케이스는 동사로 시작하고 UseCase 접미사 사용
interface GetProfilesQuery                           // 조회 전용 유스케이스는 Query 접미사 사용

// application/required (아웃바운드 포트)
interface PostRepository : Repository<Post, UUID>   // 영속성 저장소는 Repository 접미사 사용
interface BloggerStatusPort                          // 타 컨텍스트에서 조회하는 정보는 Port 접미사 또는 역할명 사용
interface PasswordHasher                             // 외부 기술 기능은 도메인 역할명으로 추상화
```

## 레이어드 아키텍처

비즈니스 흐름이 직관적이고 외부 의존이 단순한 컨텍스트(`interaction`, `analytics`, `notification`)는 별도의 포트 인터페이스 없이 4계층 구조를 사용합니다.

### 패키지 구조

인바운드 진입점(`presentation`), 응용 서비스(`application`), 도메인(`domain`), 외부 연동(`infra`)의 4계층이 각각 최상위 패키지에 대응합니다([배경 참고](#레이어드-계층-패키지-명명)).

```text
com.contentria.<컨텍스트>/
├── presentation/
│   ├── web/                     HTTP 컨트롤러
│   ├── messaging/               Kafka 이벤트 컨슈머
│   └── batch/                   Spring Batch 잡
├── application/                 응용 서비스 (유스케이스 및 트랜잭션 경계, <컨텍스트>-api 포트 구현)
├── domain/                      엔티티, 도메인 규칙, 리포지토리 인터페이스(Spring Data 마커)
├── infra/
│   ├── client/                  타 컨텍스트의 -api를 호출하는 번역 계층 (ACL)
│   ├── persistence/             SQL 집계 쿼리 등 영속성 처리
│   └── external/                외부 시스템 연동
└── config/                      채널별 Spring @Configuration 설정 클래스
```

### 계층 의존 방향

상위 계층에서 하위 계층으로의 단방향 참조만 허용하며, 리포지토리를 제외한 외부 연동 클래스(`infra`)는 응용 서비스(`application`)에서 직접 주입받아 사용합니다.

```mermaid
flowchart LR
    P["presentation"] --> A["application"]
    A --> D["domain"]
    A --> I["infra"]
    I --> D
```

1. `presentation`은 `application` 서비스만 호출하며, `domain`의 리포지토리나 `infra` 계층을 직접 호출하지 않습니다(단, 도메인 식별자나 열거형·값 타입 참조는 허용).
2. `application`은 `domain`과 `infra`를 사용하여 유스케이스를 조합합니다(리포지토리는 `domain`에 선언된 인터페이스를 통해 사용).
3. `infra`는 `domain`만 참조할 수 있으며, 상위 계층인 `application`이나 `presentation`을 참조하지 않습니다.
4. `domain`은 다른 계층에 의존하지 않습니다(JPA 애노테이션과 Spring Data `Repository` 마커만 허용).
5. 타 컨텍스트의 `-api`를 호출하는 코드는 `infra/client`에만 두며, 상대 컨텍스트의 DTO를 자기 컨텍스트의 타입으로 변환해 반환합니다.

## 공통 구현 규칙

내부 아키텍처(헥사고날, 레이어드)와 관계없이 모든 컨텍스트가 동일하게 준수하는 규칙입니다.

### 계층 경계 데이터 전달

응용 계층의 유스케이스(`application/provided` 또는 `application` 서비스)와 공개 포트(`<컨텍스트>-api`)의 입출력 시그니처에는 JPA 엔티티를 직접 반환하지 않고 불변 DTO만 사용합니다. 엔티티가 애플리케이션 경계 밖(웹 컨트롤러 등)으로 노출되면 영속성 컨텍스트 밖에서 지연 로딩(`LazyInitializationException`) 오류가 발생하거나 JSON 직렬화 과정에서 의도치 않은 연관 데이터가 조회될 수 있기 때문입니다.

```kotlin
// 권장: 필요한 값만 담은 불변 DTO 반환
data class AuthenticatedAccount(val accountId: UUID, val roles: Set<String>)

// 금지: 영속성 엔티티를 계층 밖으로 그대로 반환
fun authenticate(email: String, password: String): Account
```

### 리포지토리 설계

리포지토리 인터페이스는 Spring Data의 마커 인터페이스인 `Repository<T, ID>`를 상속하며, 실제로 사용하는 메서드만 명시적으로 선언합니다([배경 참고](#리포지토리-마커-인터페이스-채택)). 런타임 구현체는 Spring Data가 자동 생성하며, 메서드 이름만으로 표현하기 어려운 쿼리는 엔티티 클래스에 `@NamedQuery`로 정의하고 리포지토리에 동일한 이름의 메서드를 선언해 연결합니다.

```kotlin
// 엔티티: 매핑 정보가 있는 곳에 쿼리를 함께 응집시킵니다. 위치 기반 파라미터(?1)를 사용하면 리포지토리에 @Param이 필요 없습니다.
@Entity
@NamedQuery(
    name = "Comment.findThread",
    query = "select c from Comment c where c.postId = ?1 order by c.createdAt",
)
class Comment

// 리포지토리: 실제로 필요한 메서드만 선언합니다. findThread는 엔티티의 Comment.findThread 쿼리를 실행합니다.
interface CommentRepository : Repository<Comment, UUID> {
    fun save(comment: Comment): Comment
    fun findById(id: UUID): Comment?
    fun existsByPostIdAndAuthorId(postId: UUID, authorId: UUID): Boolean
    fun findThread(postId: UUID): List<Comment>
}
```

| 작성 규칙                                                                                                          | 이유                                                                                                         |
| ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| `JpaRepository`나 `CrudRepository` 대신 마커 인터페이스인 `Repository`를 상속합니다.                               | 도메인에서 허용하지 않는 메서드(`deleteAll`, `findAll` 등)가 무분별하게 노출되는 것을 방지합니다.            |
| 리포지토리 인터페이스에 `@Query`, `@Lock`, `@EntityGraph`, `@Param`을 붙이지 않고 엔티티의 `@NamedQuery`로 둡니다. | JPQL 쿼리 문장과 락 전략은 영속성 매핑 세부사항이므로, 이미 JPA 매핑이 존재하는 엔티티 클래스에 함께 둡니다. |
| 메서드 시그니처에 `Page`, `Pageable`, `Sort`, `Specification` 등 Spring Data 전용 타입을 쓰지 않습니다.            | 응용·도메인 계층이 Spring Data 기술 API에 결합되지 않도록 의존성을 마커 인터페이스 하나로 제한합니다.        |
| 메서드 이름이 유비쿼터스 언어와 어긋나면 쿼리 메서드 파싱에 맞추지 않고 `@NamedQuery`로 작성합니다.                | 프레임워크의 메서드 명명 규칙 때문에 도메인 언어가 왜곡되는 것을 방지합니다.                                 |
| 애그리거트 루트당 하나의 리포지토리만 둡니다.                                                                      | 영속성 저장과 조회의 단위를 애그리거트 경계와 일치시킵니다.                                                  |

- **테스트 방식**: `Repository` 마커 인터페이스에는 기본 상속 메서드가 없으므로 단위 테스트에서는 인메모리 Fake 구현체를 손쉽게 작성해 주입할 수 있습니다. `@NamedQuery` 문법과 실제 쿼리 동작은 Testcontainers 기반 PostgreSQL 통합 테스트로 검증하며, 쿼리 문법 오류가 있으면 스프링 컨텍스트 기동 시점에 즉시 실패합니다.
- **전제 조건**: 이 방식은 도메인 모델이 곧 JPA `@Entity`인 경우에 성립합니다. 만약 도메인 모델과 JPA 엔티티를 물리적으로 분리해야 하는 경우에는 리포지토리를 프레임워크 무관 순수 인터페이스로 두고 영속성 어댑터에서 매핑과 함께 직접 구현합니다.

### 아키텍처 테스트 (`ArchUnit`)

문서에 정의된 패키지 구조와 의존 방향 규칙은 테스트 전용 모듈인 `platform/architecture-rules`에 구현되어 있으며, 각 컨텍스트와 실행 앱(`apps/*`)의 단위 테스트에서 이를 호출해 빌드 시점에 검증합니다.

| 검증 팩토리 및 테스트                              | 검증 내용                                                                                                                                  |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `ContextRules.forEveryContext(context, structure)` | `-api` 모듈의 Spring·JPA 무의존, 타 컨텍스트 `-api` 호출의 번역 계층(ACL)·컨슈머 격리, Spring Data `Repository` 마커 외 사용 금지          |
| `ContextRules.forHexagonal(context)`               | 공통 규칙 + `domain` 허용 패키지 화이트리스트, 내부 계층의 `adapter` 참조 금지, 진입점의 `application/service` 구현체 직접 참조 금지       |
| `ContextRules.forLayered(context)`                 | 공통 규칙 + `presentation → application → domain ← infra` 단방향 계층 의존 규칙                                                            |
| `AppRules.forApps()`                               | `apps/*` 모듈이 컨텍스트의 `-api`와 `config`만 참조하고 자체 `@Entity`를 두지 않는지 검증                                                  |
| `ContextRulesTest`                                 | 패키지 경로 오타 등으로 매칭 클래스가 0건이 되어 조용히 통과하는 것을 막기 위해, 위반 픽스처는 실패하고 준수 픽스처는 통과하는지 자가 검증 |

```kotlin
// contexts/member/member-core/src/test/.../MemberArchitectureTest.kt
@AnalyzeClasses(packages = ["com.contentria.member"], importOptions = [ImportOption.DoNotIncludeTests::class])
class MemberArchitectureTest {
    @ArchTest
    val hexagonal: ArchRule = CompositeArchRule.of(ContextRules.forHexagonal("member"))
}
```

## 설계 배경

### 컨텍스트별 아키텍처 차등 적용

초기에는 도메인 복잡도와 관계없이 모든 컨텍스트에 일률적으로 헥사고날 패키지 구조와 포트·어댑터 계층을 적용했습니다. 그러나 비즈니스 규칙이 단순한 지원·일반 컨텍스트에 모든 인바운드·아웃바운드 포트와 어댑터를 강제하면, 보호할 도메인 로직 없이 1:1 위임 인터페이스만 늘어나는 보일러플레이트가 발생했습니다.

[strategic/01-domain-overview.md](../strategic/01-domain-overview.md)의 서브도메인 분류 지침에 따라 설계 투자 비중을 차등화하기 위해, 복잡한 규칙이나 무거운 외부 연동을 가진 컨텍스트만 헥사고날을 적용하고 나머지는 4계층 레이어드 구조를 채택했습니다. 향후 서비스 분리에 필요한 것은 내부 아키텍처의 통일이 아니라 표준화된 외부 계약(`-api`)이기 때문입니다.

### 레이어드 계층 패키지 명명

레이어드 아키텍처에서는 인바운드 진입점(`web`, `messaging`, `batch`)을 `presentation/`으로, 아웃바운드 연동(`client`, `persistence`, `external`)을 `infra/`로 묶어 4계층(`presentation`, `application`, `domain`, `infra`)이 각각 최상위 패키지 하나씩에 대응하도록 구성했습니다. DDD 원서에서는 진입점 계층을 `interfaces`로 부르기도 하지만, 실무에서 훨씬 직관적으로 통용되는 `presentation` 명칭을 채택했습니다.

### 리포지토리 마커 인터페이스 채택

리포지토리에 의존성 역전(DIP)을 적용하는 세 가지 방식을 비교하여 현재의 `Repository` 마커 상속 방식을 채택했습니다.

| 설계 선택지                                                                      | 의존성 역전(DIP) | 도메인·응용 계층의 프레임워크 의존 | 애그리거트당 필요한 파일 수                  |
| -------------------------------------------------------------------------------- | ---------------- | ---------------------------------- | -------------------------------------------- |
| 순수 도메인 인터페이스 + 어댑터 구현체 + Spring Data 인터페이스 분리 (과거 방식) | 달성             | 없음                               | 3개                                          |
| **Spring Data `Repository` 마커 인터페이스를 직접 상속 (현재 방식)**             | **달성**         | **마커 인터페이스 1개**            | **1개 (복잡한 쿼리는 엔티티 `@NamedQuery`)** |
| 리포지토리 인터페이스 없이 인프라 계층 클래스를 직접 사용                        | 미달성           | 없음                               | 1개                                          |

- 과거 구조에서는 순수 인터페이스를 지키기 위해 애그리거트마다 3개의 파일을 만들었으나, 어댑터 구현체의 코드는 전부 Spring Data 인터페이스 메서드를 1:1로 단순 위임하는 코드였습니다. 메서드가 하나도 없는 마커 인터페이스(`org.springframework.data.repository.Repository`)만 상속하면 의존성 역전과 명시적 메서드 선언이라는 본질적 이점을 유지하면서도 불필요한 위임 클래스를 제거할 수 있습니다.
- 헥사고날 컨텍스트에서도 동일한 방식을 사용합니다. 헥사고날을 선택한 이유는 복잡한 도메인 규칙 보호와 외부 인프라(R2, Queues, OAuth 등) 격리이지 RDBMS 자체를 교체하기 위함이 아니므로, 리포지토리 포트에 마커 인터페이스 하나를 허용하는 실용적 타협을 택했습니다.
- 아울러 쿼리 구현체를 영속성 패키지(`adapter/required/persistence` 또는 `infra/persistence`)로 분리하기 위해 Spring Data 커스텀 Fragment(`<인터페이스>Impl`) 방식도 검토했으나 채택하지 않았습니다. Spring Data는 Fragment 구현 클래스를 리포지토리 인터페이스가 위치한 패키지 및 그 하위 패키지에서만 탐색하므로, 결국 영속성 구현 코드를 `application/required`나 `domain` 패키지 하위에 두어야 하는 모순이 발생하기 때문입니다.
