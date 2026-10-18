# 모듈 구조

최상위 축은 바운디드 컨텍스트다. 컨텍스트 안의 내부 구조(헥사고날, 레이어드)는 컨텍스트마다 고른다([02-internal-architecture.md](02-internal-architecture.md)).
모듈러 모놀리스로 운영하되, 컨텍스트 하나를 떼어 서비스로 만들 때 그 컨텍스트 디렉터리와 조립 설정만 바뀌도록 구성한다.

## 전체 구성

```
backend/
├── settings.gradle.kts
├── build-logic/                 Gradle 컨벤션 플러그인
├── shared-kernel/               모든 컨텍스트가 쓰는 공통 기반. 도메인 개념 없음
├── platform/                    기술 라이브러리
│   ├── messaging/               아웃박스, 릴레이, 인박스
│   └── architecture-rules/      아키텍처 규칙. 테스트 전용
├── contexts/
│   ├── member/
│   │   ├── member-api/
│   │   └── member-core/
│   ├── publishing/
│   │   ├── publishing-api/
│   │   └── publishing-core/
│   ├── interaction/
│   ├── media/
│   ├── analytics/
│   └── notification/
├── apps/                        조립 모듈. 컨텍스트를 묶어 프로세스로 띄운다
│   ├── api/
│   ├── batch/
│   ├── worker/
│   └── migrator/
└── docs/
```

레거시 모듈(`blog-*`)은 이 구조와 호환하지 않고 저장소에서 지웠다. 소스는 `legacy-final` 태그에 있다.

`contexts/`, `contexts/<컨텍스트>/`, `apps/`, `platform/`은 모듈을 묶는 디렉터리다. `build.gradle.kts`를 두지 않는다.
파일이 있으면 플러그인이 적용되어 빈 jar를 만들려다 빌드가 실패한다.

## 모듈 종류

| 모듈 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `shared-kernel` | 식별자 생성, 예외 기반 타입, 영속성 기반 클래스, 이벤트 봉투 | 도메인 개념. 특정 컨텍스트만 쓰는 코드 |
| `platform/*` | 여러 컨텍스트가 쓰는 기술 구현 (아웃박스 기록, 릴레이, 인박스). 테스트 전용 아키텍처 규칙 | 도메인 개념 |
| `<컨텍스트>-api` | 다른 컨텍스트에 공개하는 조회 포트, 그 입출력 DTO, 이벤트 | 구현. Spring 의존 |
| `<컨텍스트>-core` | 그 컨텍스트의 전부. 도메인, 유스케이스, 진입점(웹, 메시지 소비, 배치 잡), 저장소, 외부 연동, 마이그레이션 | 다른 컨텍스트의 구현 |
| `apps/*` | 프로세스 기동, 조립, 요청 인증, 화면 조합 | 도메인 로직 |

### shared-kernel과 platform을 나누는 이유

공유 커널은 모든 컨텍스트가 같은 뜻으로 쓰는 작은 기반이다. 바꾸면 모든 컨텍스트가 영향을 받으므로 거의 바뀌지 않아야 한다.
platform은 기술 구현이다. Kafka 클라이언트처럼 자주 바뀔 수 있다.
둘을 섞으면 기술 의존성이 공유 커널을 타고 `-api` 모듈까지 번진다. `-api`는 순수한 Kotlin으로 남아야 한다.

## 의존 규칙

| 모듈 | 의존해도 되는 것 |
|---|---|
| `shared-kernel` | 없음 |
| `platform/*` | `shared-kernel` |
| `<컨텍스트>-api` | `shared-kernel` |
| `<컨텍스트>-core` | 자기 `-api`, 허용된 상대의 `-api`, `shared-kernel`, `platform/*` |
| `apps/*` | 띄울 컨텍스트의 `-core`, `shared-kernel` |

허용된 상대는 [03-context-integration.md](03-context-integration.md)의 "허용하는 의존" 표를 따른다.
다른 컨텍스트의 `-core`는 의존성에 넣을 수 없다. 넣지 않았으므로 그 컨텍스트의 도메인 클래스를 import하면 컴파일이 실패한다.

```kotlin
// settings.gradle.kts
include("shared-kernel", "platform:messaging")
include("contexts:member:member-api", "contexts:member:member-core")
include("contexts:publishing:publishing-api", "contexts:publishing:publishing-core")
// ... 나머지 컨텍스트
include("apps:api", "apps:batch", "apps:worker", "apps:migrator")
```

```kotlin
// contexts/publishing/publishing-core/build.gradle.kts
dependencies {
    api(project(":contexts:publishing:publishing-api"))
    implementation(project(":contexts:member:member-api"))
    implementation(project(":contexts:media:media-api"))
    implementation(project(":shared-kernel"))
    implementation(project(":platform:messaging"))
}
```

```kotlin
// apps/api/build.gradle.kts
dependencies {
    implementation(project(":contexts:member:member-core"))
    implementation(project(":contexts:publishing:publishing-core"))
    implementation(project(":contexts:interaction:interaction-core"))
    // ... 이 프로세스에 띄울 컨텍스트
}
```

`apps/*`는 `-core`에 의존하지만 코드에서는 `-api`의 타입과 각 컨텍스트가 공개한 설정 클래스만 쓴다. ArchUnit으로 강제한다([02-internal-architecture.md](02-internal-architecture.md)).

## 컨텍스트 모듈

### `<컨텍스트>-api`

모든 컨텍스트가 같은 모양을 따른다.

```
contexts/publishing/publishing-api/src/main/kotlin/com/contentria/publishing/api/
├── GetPostVisibilityQuery.kt    다른 컨텍스트나 화면 조합이 호출하는 조회 포트
├── GetPostSummariesQuery.kt
├── dto/                         조회 포트의 입출력
└── event/                       이 컨텍스트가 발행하는 이벤트와 토픽 이름
```

| 규칙 | 이유 |
|---|---|
| 인터페이스와 데이터 클래스만 둔다 | 서비스로 분리하면 이 모듈이 그대로 클라이언트 라이브러리가 된다 |
| Spring, JPA에 의존하지 않는다 | 위와 같다 |
| 엔티티를 노출하지 않는다 | 엔티티는 네트워크를 건너갈 수 없다 |
| 쓰기(명령) 포트는 원칙적으로 두지 않는다 | 다른 컨텍스트의 데이터를 바꾸는 일은 이벤트로 한다 |

마지막 규칙의 예외: 출판이 미디어를 호출해 첨부를 승격하고 연결하는 것은 쓰기지만 동기 호출이다. 이런 포트는 `-api`에 두되 [strategic/04-context-map.md](../strategic/04-context-map.md)의 "동기 호출" 표에 있어야 한다.

### `<컨텍스트>-core`

내부 구조는 컨텍스트마다 다르다([02-internal-architecture.md](02-internal-architecture.md)). 모든 컨텍스트에 공통인 것은 다음과 같다.

| 위치 | 내용 |
|---|---|
| `com.contentria.<컨텍스트>.config` | 채널별 설정 클래스. `apps/*`가 import한다 |
| `src/main/resources/db/migration/<컨텍스트>/` | 그 컨텍스트 스키마의 Flyway 마이그레이션 |
| 다른 컨텍스트를 호출하는 번역 계층 | 헥사고날은 `adapter/required/<상대>/`, 레이어드는 `infra/client/` |
| 리포지토리 | Spring Data `Repository` 마커를 상속한 인터페이스. 위치는 헥사고날 `application/required/`, 레이어드 `domain/` ([02-internal-architecture.md](02-internal-architecture.md)) |

## 조립 모듈 (apps)

나누는 기준은 인바운드 방식이 아니라 **따로 떠야 하는 프로세스**다. 생명주기가 다르기 때문이다.

| 모듈 | 생명주기 | 담는 컨텍스트 | 특성 |
|---|---|---|---|
| `api` | 상주 | 전부 | HTTP 진입점, Kafka 소비자, 아웃박스 릴레이. 무중단 배포, 헬스체크 |
| `batch` | 원샷 | member, media, analytics | 실행 후 종료 코드 반환. CronJob이 띄운다 |
| `worker` | 상주 | media | Cloudflare Queue 소비, 영상 변환 |
| `migrator` | 원샷 | 전부 (마이그레이션만) | 배포 전에 ArgoCD PreSync Job으로 실행 |

배치가 API와 같은 프로세스에 있으면 배치 작업의 메모리 사용이 API 응답에 영향을 준다. 따로 확장할 수도 없다.

### 컨텍스트를 불러오는 방식

각 컨텍스트는 채널별 설정 클래스를 공개한다. app은 띄울 채널의 설정 클래스만 import한다.
app은 컨텍스트의 패키지 구조를 모른다. 컨텍스트마다 내부 구조가 다르기 때문이다.

```kotlin
// contexts/interaction/interaction-core/.../config/InteractionWebConfiguration.kt
@Configuration
@ComponentScan(basePackageClasses = [CommentController::class])
class InteractionWebConfiguration

// apps/api/.../ApiApplication.kt
@SpringBootApplication
@Import(
    PublishingWebConfiguration::class, PublishingMessagingConfiguration::class,
    InteractionWebConfiguration::class, InteractionMessagingConfiguration::class,
    // ...
)
class ApiApplication
```

| 채널 | 설정 클래스 이름 | 불러오는 app |
|---|---|---|
| HTTP | `<컨텍스트>WebConfiguration` | api |
| Kafka 소비 | `<컨텍스트>MessagingConfiguration` | api |
| 배치 잡 | `<컨텍스트>BatchConfiguration` | batch |
| 외부 큐 소비 | `<컨텍스트>QueueConfiguration` | worker |
| 공통 (서비스, 저장소) | `<컨텍스트>CoreConfiguration` | 그 컨텍스트를 띄우는 모든 app |

### apps/api에만 있는 것

```
apps/api/src/main/kotlin/com/contentria/app/api/
├── ApiApplication.kt
├── security/                    JWT 필터, 요청 인증
└── composition/                 화면 조합. 여러 컨텍스트의 -api를 호출
    ├── PostDetailQuery.kt
    └── BlogDashboardQuery.kt
```

화면 조합은 어느 컨텍스트에도 속하지 않는다. 서비스로 분리하면 이 부분이 BFF가 된다.
관리자 기능을 만들면 `apps/admin`을 같은 방식으로 둔다. 관리자는 컨텍스트가 아니다.

## 패키지 이름

| 모듈 | 패키지 |
|---|---|
| `shared-kernel` | `com.contentria.shared` |
| `platform/messaging` | `com.contentria.platform.messaging` |
| `<컨텍스트>-api` | `com.contentria.<컨텍스트>.api` |
| `<컨텍스트>-core` | `com.contentria.<컨텍스트>` (`api` 하위 패키지는 쓰지 않는다) |
| `apps/<이름>` | `com.contentria.app.<이름>` |

같은 패키지를 두 jar가 나눠 갖지 않게 한다. `-api`와 `-core`가 같은 패키지를 쓰면 어느 jar의 클래스인지 패키지만으로 구분할 수 없다.

## 컨벤션 플러그인

`build-logic`에 정의한다. 루트 `build.gradle.kts`에 `subprojects` 블록을 쓰지 않는다. 모듈 성격이 여러 가지로 갈리는데 `subprojects`는 이를 구분할 수 없다.

| 플러그인 | 적용 대상 | 역할 | 상태 |
|---|---|---|---|
| `contentria-kotlin-common-conventions` | 전체 | 툴체인, 테스트, 컴파일 옵션 | 있음 |
| `contentria-kotlin-library-conventions` | `<컨텍스트>-api`, `shared-kernel`, `platform/architecture-rules` | Spring Boot 플러그인 없이 BOM으로 버전만 관리. Spring 런타임에 의존하지 않는다 | 있음 |
| `contentria-spring-library-conventions` | `<컨텍스트>-core`, `platform/*` | Boot 플러그인, bootJar 비활성화 | 있음 |
| `contentria-jpa-conventions` | 저장소를 가진 `<컨텍스트>-core`, `shared-kernel` | noarg, allOpen | 있음 |
| `contentria-spring-boot-app-conventions` | `apps/*` | Boot 플러그인 | 있음 |

`gradle.properties`에서 Kotlin의 Build Tools API 경로를 끈다(`kotlin.compiler.runViaBuildToolsApi=false`). Kotlin 2.4.10에서 Spring Boot 플러그인이 없는 모듈을 컴파일할 때 NullPointerException이 나기 때문이다. Kotlin을 올릴 때 다시 확인한다.

## 서비스로 분리할 때

알림을 분리하는 경우를 예로 든다.

```
contexts/notification/
├── notification-api/            그대로
├── notification-core/           번역 계층 하나만 바뀐다
└── notification-app/            새로 만든다. NotificationApplication.kt

apps/api/build.gradle.kts        notification-core 의존과 설정 클래스 import를 지운다
```

| 바뀌는 것 | 내용 |
|---|---|
| 번역 계층 | 구독자 목록을 메모리 호출로 얻던 것을 HTTP 클라이언트로 바꾼다 |
| 데이터베이스 | `notification` 스키마를 덤프해 새 데이터베이스로 옮긴다 |
| 이벤트 | 바뀌지 않는다. 모놀리스에서도 Kafka로 주고받기 때문이다 |
| `notification-api` 소비자 | 모노레포 안의 프로젝트 의존을 라이브러리 의존으로 바꾼다 |

## 현재 상태

| 모듈 | 상태 |
|---|---|
| `shared-kernel` | 있음. 식별자 생성, 예외 기반, `AuditableEntity` |
| `contexts/member` | `member-core`에 도메인 모델(계정, 프로필)만 있음. `member-api`는 비어 있음 |
| `contexts/publishing`, `interaction`, `media`, `analytics`, `notification` | 없음 |
| `platform/messaging` | 없음 |
| `platform/architecture-rules` | 있음. 헥사고날 규칙과 app 규칙. 레이어드 규칙은 없음 |
| `apps/api` | 있음. 조립만 함 (member) |
| `apps/batch` | 있음. 잡 없음 |
| `apps/worker`, `apps/migrator` | 없음 |

## 검증

```bash
./gradlew projects                                   # 모듈 트리 확인
./gradlew :contexts:member:member-api:dependencies   # -api가 Spring을 끌고 오지 않는지
./gradlew :apps:api:bootJar
```

## 결정과 이유

### 최상위를 기술 축에서 컨텍스트 축으로 바꾼다

**이전 결정:** `core`(도메인과 바깥으로 나가는 어댑터 전부)와 `app`(들어오는 어댑터)을 최상위에 두고, 컨텍스트는 `core` 안의 패키지로 나눴다.

**뒤집은 이유:**

- 모든 컨텍스트가 `core` 모듈 하나에 있어 컴파일러가 경계를 지켜주지 않았다. 다른 컨텍스트의 도메인 클래스를 import해도 빌드가 통과했다.
- 아키텍처 선택(헥사고날)이 최상위를 차지해 컨텍스트마다 내부 구조를 고를 수 없었다.
- 컨텍스트 하나를 서비스로 떼려면 `core`와 `app` 양쪽에서 코드를 골라내야 했다.

### 진입점을 컨텍스트 안에 둔다

**이전 결정:** `app` 모듈 전체를 인바운드 어댑터로 보고 컨트롤러를 그곳에 두었다.

**뒤집은 이유:** 컨텍스트가 자기 진입점까지 가져야 디렉터리째 떼어낼 수 있다. app에 남는 것은 조립, 요청 인증, 화면 조합처럼 어느 컨텍스트에도 속하지 않는 것뿐이다.
