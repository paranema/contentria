# 모듈 구조 (Module Structure)

백엔드 코드베이스의 최상위 축은 바운디드 컨텍스트입니다. 각 컨텍스트의 내부 아키텍처(헥사고날, 레이어드)는 컨텍스트의 성격에 맞게 개별적으로 선택합니다([02-internal-architecture.md](02-internal-architecture.md) 참고).
평소에는 모듈러 모놀리스로 단순하게 운영하되, 향후 특정 컨텍스트를 독립 서비스로 분리할 때 해당 컨텍스트 디렉터리와 조립 설정만 수정하면 되도록 모듈 경계를 구성합니다.

## 전체 디렉터리 및 모듈 구성

```text
backend/
├── settings.gradle.kts
├── build-logic/                 Gradle 컨벤션 플러그인
├── shared-kernel/               모든 컨텍스트가 공유하는 공통 기반 모듈 (도메인 개념 없음)
├── platform/                    공통 기술 인프라 라이브러리
│   ├── messaging/               Transactional Outbox, Relay, Inbox
│   └── architecture-rules/      ArchUnit 아키텍처 검증 규칙 (테스트 전용)
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
├── apps/                        컨텍스트를 조합해 프로세스로 기동하는 실행 모듈
│   ├── api/
│   ├── batch/
│   ├── worker/
│   └── migrator/
└── docs/
```

- `contexts/`, `contexts/<컨텍스트>/`, `apps/`, `platform/`과 같은 중간 그룹 디렉터리에는 `build.gradle.kts` 파일을 두지 않습니다. 중간 디렉터리에 빌드 파일이 존재하면 Gradle 플러그인이 일괄 적용되어 빈 JAR를 생성하려다 빌드가 실패할 수 있습니다.

## 모듈 유형별 역할

| 모듈 유형         | 포함하는 요소                                                                                           | 포함하지 않는 요소                             |
| ----------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| `shared-kernel`   | 식별자 생성, 공통 예외 베이스 타입, 영속성 공통 기반 클래스(`AuditableEntity`)                          | 비즈니스 도메인 개념, 특정 컨텍스트 전용 코드  |
| `platform/*`      | 여러 컨텍스트가 공통으로 사용하는 기술 구현(이벤트 봉투, Outbox 기록기·릴레이, Inbox), 아키텍처 규칙    | 비즈니스 도메인 개념                           |
| `<컨텍스트>-api`  | 타 컨텍스트에 공개하는 조회·명령 포트 인터페이스, 입출력 DTO, 발행하는 도메인 이벤트 및 토픽 상수       | 비즈니스 구현체, Spring·JPA 등 프레임워크 의존 |
| `<컨텍스트>-core` | 해당 컨텍스트의 도메인 모델, 유스케이스, 진입점(웹·메시지 소비·배치), 리포지토리, 외부 연동, Flyway SQL | 다른 컨텍스트의 내부 구현                      |
| `apps/*`          | 프로세스 기동(`main`), 컨텍스트 조립, HTTP 요청 인증 필터, 화면용 조회 조합(`composition`)              | 개별 컨텍스트의 도메인 로직                    |

### `shared-kernel`과 `platform`을 분리하는 이유

- `shared-kernel`은 모든 컨텍스트의 `-api`와 `-core`가 공통으로 참조하는 최소한의 기반입니다. 이곳의 코드가 변경되면 전체 모듈이 영향을 받으므로 변경 빈도가 매우 낮아야 하며 외부 기술 의존성을 갖지 않아야 합니다.
- `platform`은 Kafka 메시징 처리처럼 기술적 구현 상세를 담는 모듈로, 프레임워크나 라이브러리 버전에 따라 비교적 자주 변경될 수 있습니다.
- 만약 두 모듈을 합치면 Kafka나 Spring 등 무거운 기술 의존성이 `shared-kernel`을 타고 `<컨텍스트>-api` 모듈까지 전파됩니다. `-api` 모듈을 순수 Kotlin 계약 모듈로 유지하기 위해 두 영역을 엄격히 분리합니다.

## 모듈 간 의존 규칙

| 기준 모듈         | 의존 가능한 대상 모듈                                                                      |
| ----------------- | ------------------------------------------------------------------------------------------ |
| `shared-kernel`   | 없음                                                                                       |
| `platform/*`      | `shared-kernel`                                                                            |
| `<컨텍스트>-api`  | `shared-kernel`                                                                            |
| `<컨텍스트>-core` | 자기 컨텍스트의 `-api`, 허용된 상대 컨텍스트의 `-api`, `shared-kernel`, `platform/*`       |
| `apps/*`          | 해당 프로세스에 포함할 컨텍스트의 `-core`, `shared-kernel`, `platform:messaging`(릴레이용) |

- 컨텍스트 간에 허용된 `-api` 의존 방향은 [03-context-integration.md](03-context-integration.md)의 `허용하는 의존` 표를 따릅니다.
- 다른 컨텍스트의 `-core` 모듈은 Gradle 의존성에 추가하지 않으므로, 타 컨텍스트의 도메인 클래스나 내부 구현체를 `import`하는 시도는 컴파일 단계에서 원천 차단됩니다.

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
    // ... 이 프로세스에서 실행할 컨텍스트 모듈
}
```

`apps/*` 모듈은 조립을 위해 각 컨텍스트의 `-core`를 의존성에 포함하지만, 실제 코드에서는 `-api`에 공개된 포트·DTO와 각 컨텍스트의 `@Configuration` 설정 클래스만 참조할 수 있습니다. 이 규칙은 ArchUnit 테스트로 강제합니다([02-internal-architecture.md](02-internal-architecture.md) 참고).

## 컨텍스트 모듈 상세

### `<컨텍스트>-api`

내부 아키텍처와 무관하게 모든 컨텍스트의 `-api` 모듈은 동일한 구조를 따릅니다.

```text
contexts/publishing/publishing-api/src/main/kotlin/com/contentria/publishing/api/
├── GetPostVisibilityQuery.kt    타 컨텍스트나 화면 조합(composition)이 호출하는 공개 포트
├── GetPostSummariesQuery.kt
├── dto/                         공개 포트의 입출력 DTO
└── event/                       이 컨텍스트가 발행하는 도메인 이벤트 페이로드 및 토픽 상수
```

| 작성 규칙                                            | 이유                                                                                              |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 인터페이스와 불변 데이터 클래스(`data class`)만 둔다 | 향후 마이크로서비스로 분리할 때 이 모듈을 그대로 클라이언트 SDK(라이브러리)로 활용할 수 있습니다. |
| Spring, JPA 등 프레임워크 의존성을 두지 않는다       | 위와 동일합니다.                                                                                  |
| JPA 엔티티나 도메인 모델을 외부로 노출하지 않는다    | 영속성 엔티티와 지연 로딩 프록시는 네트워크 경계를 넘어 전달할 수 없습니다.                       |
| 상태 변경(명령) 포트는 원칙적으로 두지 않는다        | 타 컨텍스트의 상태 변경을 유발하는 후속 처리는 도메인 이벤트로 비동기 전파하는 것이 원칙입니다.   |

> **예외**: 게시글 저장·삭제 시 출판 컨텍스트가 미디어 컨텍스트를 호출해 이미지·영상 연결 상태를 동기화하는 작업은 상태 변경이지만 즉시 반영이 필요하므로 동기 호출 포트로 제공합니다. 이처럼 예외적으로 허용하는 명령 포트는 반드시 [strategic/04-context-map.md](../strategic/04-context-map.md)의 `동기 호출` 표에 명시되어야 합니다.

### `<컨텍스트>-core`

내부 패키지 구조는 헥사고날 또는 레이어드 선택에 따라 달라지지만([02-internal-architecture.md](02-internal-architecture.md)), 다음 요소는 모든 컨텍스트에 공통으로 존재합니다.

| 구성 요소                        | 위치 및 설명                                                                                                              |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 채널별 Spring 설정 클래스        | `com.contentria.<컨텍스트>.config` 패키지에 위치하며, `apps/*` 모듈이 필요한 채널만 선택해 `@Import`합니다.               |
| Flyway 마이그레이션 스크립트     | `src/main/resources/db/migration/<컨텍스트>/` 경로에 해당 컨텍스트 스키마의 DDL을 관리합니다.                             |
| 타 컨텍스트 호출 번역 계층 (ACL) | 헥사고날은 `adapter/required/<상대>/`, 레이어드는 `infra/client/`에 위치합니다.                                           |
| 리포지토리 인터페이스            | Spring Data의 `Repository` 마커 인터페이스를 상속하며, 헥사고날은 `application/required/`, 레이어드는 `domain/`에 둡니다. |

## 실행 조립 모듈 (`apps`)

`apps/*` 모듈을 나누는 기준은 인바운드 프로토콜의 종류가 아니라 **독립된 생명주기와 확장 전략을 가져야 하는 운영 프로세스 단위**입니다. 예를 들어 배치 작업을 API 서버와 같은 프로세스에서 실행하면 대량 데이터 처리 시의 메모리 사용량이 API 응답 지연으로 이어질 수 있고, 독립적인 스케일링도 불가능하기 때문에 별도 프로세스로 분리합니다.

| 모듈       | 실행 형태  | 포함하는 컨텍스트              | 주요 역할 및 운영 특성                                                          |
| ---------- | ---------- | ------------------------------ | ------------------------------------------------------------------------------- |
| `api`      | 상주 서버  | 전체 (6개 컨텍스트)            | HTTP API 제공, Kafka 이벤트 소비, Outbox 릴레이 실행 (무중단 배포 대상)         |
| `batch`    | 일회성 Job | `member`, `media`, `analytics` | 지정된 배치 Job을 1회 실행 후 종료 코드 반환 (Kubernetes CronJob 등으로 실행)   |
| `worker`   | 상주 워커  | `media`                        | Cloudflare Queues에서 업로드 이벤트를 Pull하여 FFmpeg 영상 변환 수행            |
| `migrator` | 일회성 Job | 전체 (마이그레이션 SQL만)      | 배포 시 애플리케이션 기동 전에 컨텍스트별 DB 마이그레이션 수행 (ArgoCD PreSync) |

### 컨텍스트 모듈을 조립하는 방식

각 컨텍스트는 진입 채널(웹, 메시징, 배치, 큐)별로 `@Configuration` 클래스를 제공합니다. 실행 앱(`apps/*`)은 컨텍스트 내부 패키지 구조를 알 필요 없이 자신이 띄울 채널의 설정 클래스만 명시적으로 `@Import`합니다.

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

| 실행 채널                | 설정 클래스 명명 규칙              | `@Import`하는 실행 앱            |
| ------------------------ | ---------------------------------- | -------------------------------- |
| HTTP 웹 진입점           | `<컨텍스트>WebConfiguration`       | `api`                            |
| Kafka 이벤트 소비자      | `<컨텍스트>MessagingConfiguration` | `api`                            |
| Spring Batch 잡          | `<컨텍스트>BatchConfiguration`     | `batch`                          |
| 외부 큐(R2 이벤트) 소비  | `<컨텍스트>QueueConfiguration`     | `worker`                         |
| 도메인 서비스·리포지토리 | `<컨텍스트>CoreConfiguration`      | 해당 컨텍스트를 사용하는 모든 앱 |

### `apps/api` 고유 구성 요소

```text
apps/api/src/main/kotlin/com/contentria/app/api/
├── ApiApplication.kt
├── security/                    JWT 인증 필터 및 요청 인가 설정
└── composition/                 여러 컨텍스트의 -api를 조합하는 화면 전용 조회 계층
    ├── PostDetailQuery.kt
    └── BlogDashboardQuery.kt
```

화면 응답을 위해 여러 컨텍스트의 데이터를 조합하는 `composition` 계층은 특정 컨텍스트 하나에 속하지 않으므로 `apps/api`에 위치합니다. 추후 컨텍스트들을 마이크로서비스로 분리할 경우 이 계층이 자연스럽게 BFF(Backend For Frontend) 역할을 맡게 됩니다.

## 패키지 명명 규칙

| 모듈                 | 기본 패키지 경로                                                  |
| -------------------- | ----------------------------------------------------------------- |
| `shared-kernel`      | `com.contentria.shared`                                           |
| `platform/messaging` | `com.contentria.platform.messaging`                               |
| `<컨텍스트>-api`     | `com.contentria.<컨텍스트>.api`                                   |
| `<컨텍스트>-core`    | `com.contentria.<컨텍스트>` (하위에 `api` 패키지는 사용하지 않음) |
| `apps/<이름>`        | `com.contentria.app.<이름>`                                       |

서로 다른 JAR 모듈이 동일한 패키지 경로를 공유(Split Package)하지 않도록 격리합니다. `<컨텍스트>-api`와 `<컨텍스트>-core`의 패키지 네임스페이스를 명확히 분리해야 클래스의 패키지 경로만으로도 소속 모듈을 즉시 식별할 수 있습니다.

## Gradle 컨벤션 플러그인

모듈별 공통 빌드 설정은 루트 `build.gradle.kts`의 `subprojects` 블록 대신 `build-logic`의 컨벤션 플러그인으로 정의합니다. 모듈마다 순수 Kotlin 라이브러리, Spring 라이브러리, 실행 가능한 Spring Boot 앱 등 성격이 다르므로 각 모듈이 자신에게 맞는 컨벤션 플러그인만 명시적으로 적용합니다.

| 컨벤션 플러그인                          | 적용 대상 모듈                                                   | 주요 역할                                                                 |
| ---------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `contentria-kotlin-common-conventions`   | 전체 모듈                                                        | JDK 툴체인, JUnit 5 테스트 설정, Kotlin 컴파일러 공통 옵션                |
| `contentria-kotlin-library-conventions`  | `<컨텍스트>-api`, `shared-kernel`, `platform/architecture-rules` | Spring Boot 플러그인 없이 BOM으로 버전만 관리 (Spring 런타임 의존성 배제) |
| `contentria-spring-library-conventions`  | `<컨텍스트>-core`, `platform/*`                                  | Spring 의존성 관리 적용 및 실행 JAR(`bootJar`) 생성 비활성화              |
| `contentria-jpa-conventions`             | JPA 엔티티를 포함하는 `<컨텍스트>-core`, `shared-kernel`         | Kotlin `noarg`, `allOpen` 컴파일러 플러그인 적용                          |
| `contentria-spring-boot-app-conventions` | `apps/*`                                                         | 실행 가능한 Spring Boot 애플리케이션(`bootJar`) 빌드                      |

> **참고**: `gradle.properties`에서 `kotlin.compiler.runViaBuildToolsApi=false`로 설정하여 Kotlin Build Tools API 경로를 비활성화했습니다. Kotlin 2.4.10에서 Spring Boot 플러그인이 적용되지 않은 순수 라이브러리 모듈을 컴파일할 때 발생하는 `NullPointerException` 이슈를 우회하기 위한 설정이며, 추후 Kotlin 버전 업그레이드 시 재검토합니다.

## 향후 마이크로서비스 분리 시 변경 범위

예를 들어 `notification`(알림) 컨텍스트를 독립된 마이크로서비스로 분리할 때의 변경 범위는 다음과 같습니다.

```text
contexts/notification/
├── notification-api/            변경 없음 (클라이언트 계약 유지)
├── notification-core/           타 컨텍스트 호출 번역 계층(ACL) 구현체만 변경
└── notification-app/            신규 생성 (독립 기동용 NotificationApplication.kt)

apps/api/build.gradle.kts        notification-core 의존성 및 설정 클래스 @Import 제거
```

| 영역                       | 분리 시 변경 내용                                                                          |
| -------------------------- | ------------------------------------------------------------------------------------------ |
| 타 컨텍스트 호출 번역 계층 | 구독자 목록 조회를 인메모리 메서드 호출에서 HTTP/gRPC 클라이언트 호출로 교체합니다.        |
| 데이터베이스               | 기존 PostgreSQL의 `notification` 스키마만 덤프하여 신규 데이터베이스로 이전합니다.         |
| 비동기 도메인 이벤트       | 변경 없습니다. 모듈러 모놀리스 단계에서도 이미 Kafka를 통해 이벤트를 주고받기 때문입니다.  |
| `notification-api` 참조처  | 모노레포 외부로 분리하는 경우에만 Gradle 프로젝트 의존성을 아티팩트 의존성으로 변경합니다. |

## 모듈 구성 검증 명령어

```bash
./gradlew projects                                   # 전체 Gradle 모듈 트리 확인
./gradlew :contexts:member:member-api:dependencies   # -api 모듈에 Spring 의존성이 유입되지 않았는지 확인
./gradlew :apps:api:bootJar                          # API 서버 실행 JAR 패키징 검증
```

## 결정과 이유

### 1. 최상위 모듈 분할 축을 '기술 계층'에서 '바운디드 컨텍스트'로 전환합니다

- **이전 결정**: 최상위 모듈을 `core`(도메인 및 아웃바운드 어댑터 전체)와 `app`(인바운드 웹 어댑터)으로 나누고, 각 바운디드 컨텍스트는 `core` 모듈 내부의 패키지로만 구분했습니다.
- **변경 이유**:
  - 모든 컨텍스트가 단일 `core` 모듈 안에 공존하다 보니 컴파일러 수준에서 컨텍스트 간 경계를 강제할 수 없었고, 다른 컨텍스트의 내부 도메인 클래스를 직접 참조해도 빌드가 그대로 통과했습니다.
  - 최상위 구조가 특정 아키텍처(헥사고날)에 종속되어 컨텍스트별로 적합한 내부 구조(헥사고날 vs 레이어드)를 다르게 선택할 수 없었습니다.
  - 특정 컨텍스트를 별도 서비스로 분리하려면 `core`와 `app` 양쪽 모듈에서 관련 코드를 일일이 발라내야 했습니다.

### 2. 인바운드 진입점(컨트롤러·컨슈머·배치)을 각 컨텍스트 모듈 내부에 둡니다

- **이전 결정**: `app` 모듈 전체를 인바운드 어댑터 계층으로 간주하여 모든 웹 컨트롤러를 `app` 모듈에 두었습니다.
- **변경 이유**: 컨텍스트 모듈이 자신의 인바운드 진입점까지 함께 소유해야 향후 해당 디렉터리만 그대로 떼어내 독립 서비스로 기동할 수 있습니다. 결과적으로 `apps/*` 모듈에는 프로세스 조립, 공통 인증 필터, 다중 컨텍스트 화면 조합(`composition`)처럼 특정 컨텍스트 하나에 귀속되지 않는 요소만 남게 됩니다.
