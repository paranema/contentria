# 모듈 구조

백엔드 코드베이스는 기술 계층이 아닌 바운디드 컨텍스트를 최상위 축으로 삼아 모듈을 구성합니다([배경 참고](#바운디드-컨텍스트-기준-분할)). 평소에는 모듈러 모놀리스로 단순하게 운영하되, 향후 특정 컨텍스트를 독립 서비스로 분리할 때 해당 컨텍스트 디렉터리와 조립 설정만 수정하면 되도록 모듈 경계를 격리합니다.

## 개요

### 디렉터리 구조

전체 백엔드 코드베이스는 **공통 모듈**(`shared-kernel`, `platform`), 도메인별 **바운디드 컨텍스트 모듈**(`contexts`), 그리고 이들을 조합해 프로세스로 기동하는 **실행 조립 모듈**(`apps`)의 세 영역으로 나뉩니다.

```text
backend/
├── settings.gradle.kts
├── build-logic/                 모듈 유형별 공통 Gradle 빌드 규칙 (컨벤션 플러그인)
├── shared-kernel/               공통 모듈: 도메인 무관 최소 공통 기반
├── platform/                    공통 모듈: 기술 인프라 라이브러리
│   ├── messaging/               Transactional Outbox, Relay, Inbox
│   └── architecture-rules/      ArchUnit 아키텍처 검증 규칙 (테스트 전용)
├── contexts/                    바운디드 컨텍스트 모듈
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
├── apps/                        실행 조립 모듈
│   ├── api/
│   ├── batch/
│   ├── worker/
│   └── migrator/
└── docs/
```

- `build-logic/`은 런타임에 포함되는 애플리케이션 모듈이 아니라, 각 모듈의 성격(순수 Kotlin 라이브러리, Spring 라이브러리, Spring Boot 실행 앱)에 맞는 공통 빌드 설정을 정의하는 Gradle 컨벤션 플러그인 디렉터리입니다.
- `contexts/`, `contexts/<컨텍스트>/`, `apps/`, `platform/`과 같이 하위 모듈을 묶기만 하는 중간 그룹 디렉터리에는 `build.gradle.kts`를 두지 않습니다. 중간 디렉터리에 빌드 파일이 있으면 Gradle 플러그인이 일괄 적용되어 빈 JAR를 생성하려다 빌드가 실패할 수 있기 때문입니다.

### 모듈 유형

세 영역을 구성하는 5가지 모듈 유형의 역할과 포함 범위입니다.

| 분류                   | 모듈              | 역할                                     | 포함하는 요소                                                                                           | 포함하지 않는 요소                             |
| ---------------------- | ----------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| 공통 모듈              | `shared-kernel`   | 모든 모듈이 공유하는 최소 공통 기반      | 식별자 생성, 공통 예외 베이스 타입, 영속성 공통 기반 클래스(`AuditableEntity`)                          | 비즈니스 도메인 개념, 외부 기술 의존성         |
| 공통 모듈              | `platform/*`      | 여러 컨텍스트가 재사용하는 기술 인프라   | 메시징 기술 구현(이벤트 봉투, Outbox 기록기·릴레이, Inbox), 아키텍처 검증 규칙                          | 비즈니스 도메인 개념                           |
| 바운디드 컨텍스트 모듈 | `<컨텍스트>-api`  | 타 컨텍스트에 공개하는 도메인 계약       | 공개 조회·명령 포트 인터페이스, 입출력 DTO, 발행하는 도메인 이벤트 및 토픽 상수                         | 비즈니스 구현체, Spring·JPA 등 프레임워크 의존 |
| 바운디드 컨텍스트 모듈 | `<컨텍스트>-core` | 컨텍스트의 비즈니스 로직 및 진입점 구현  | 해당 컨텍스트의 도메인 모델, 유스케이스, 진입점(웹·메시지 소비·배치), 리포지토리, 외부 연동, Flyway SQL | 다른 컨텍스트의 내부 구현                      |
| 실행 조립 모듈         | `apps/*`          | 운영 프로세스 기동 및 다중 컨텍스트 조립 | 프로세스 기동(`main`), 컨텍스트 설정 조립, HTTP 요청 인증 필터, 화면용 조회 조합(`composition`)         | 개별 컨텍스트의 도메인 로직                    |

### 모듈 간 의존 규칙

컴파일 타임에 컨텍스트 간 경계를 강제하기 위해 모듈마다 참조할 수 있는 대상 모듈을 제한합니다. 다른 컨텍스트의 `-core` 모듈은 Gradle 의존성에 추가하지 않으므로, 타 컨텍스트의 도메인 모델이나 내부 구현체를 `import`하는 시도는 빌드 단계에서 원천 차단됩니다.

| 기준 모듈         | 의존 가능한 대상 모듈                                                                      |
| ----------------- | ------------------------------------------------------------------------------------------ |
| `shared-kernel`   | 없음                                                                                       |
| `platform/*`      | `shared-kernel`                                                                            |
| `<컨텍스트>-api`  | `shared-kernel`                                                                            |
| `<컨텍스트>-core` | 자기 컨텍스트의 `-api`, 허용된 상대 컨텍스트의 `-api`, `shared-kernel`, `platform/*`       |
| `apps/*`          | 해당 프로세스에 포함할 컨텍스트의 `-core`, `shared-kernel`, `platform:messaging`(릴레이용) |

- 컨텍스트 간에 허용된 `-api` 의존 방향은 [03-context-integration.md](03-context-integration.md)의 허용 의존 표를 따릅니다.
- `apps/*` 모듈은 빈 조립을 위해 각 컨텍스트의 `-core`를 의존성에 포함하지만, 실제 코드에서는 `-api`에 공개된 포트·DTO와 각 컨텍스트의 `@Configuration` 설정 클래스만 참조할 수 있습니다. 이 제약은 ArchUnit 테스트로 검증합니다([02-context-architecture.md](02-context-architecture.md) 참고).

```kotlin
// contexts/publishing/publishing-core/build.gradle.kts 예시
dependencies {
    api(project(":contexts:publishing:publishing-api"))
    implementation(project(":contexts:member:member-api"))
    implementation(project(":contexts:media:media-api"))
    implementation(project(":shared-kernel"))
    implementation(project(":platform:messaging"))
}
```

## 공통 모듈

모든 바운디드 컨텍스트가 공통으로 사용하는 코드는 변경 빈도와 기술 의존성 유무에 따라 `shared-kernel`과 `platform` 두 모듈로 엄격히 분리합니다. 만약 두 모듈을 하나로 합치면 Spring이나 Kafka 같은 무거운 기술 의존성이 `shared-kernel`을 타고 `<컨텍스트>-api` 모듈까지 전파되기 때문입니다.

### 최소 공통 기반 (`shared-kernel`)

`shared-kernel`은 모든 컨텍스트의 `-api`와 `-core`가 공통으로 참조하는 최하위 모듈입니다. 이곳의 코드가 변경되면 전체 모듈이 영향을 받으므로 비즈니스 도메인 개념이나 외부 프레임워크 의존성을 두지 않으며, 식별자 생성이나 공통 예외 베이스 타입처럼 변경 빈도가 극히 낮은 요소만 포함합니다.

### 기술 인프라 (`platform`)

`platform`은 여러 컨텍스트의 `-core` 모듈이 공통으로 사용하는 기술 구현 상세를 담는 모듈입니다. Transactional Outbox 기록기·릴레이 및 Inbox 처리를 담당하는 `platform:messaging`과 ArchUnit 기반의 아키텍처 검증 규칙을 제공하는 `platform:architecture-rules`로 구성되며, 프레임워크나 인프라 라이브러리 버전에 따라 비교적 자주 변경될 수 있습니다.

## 바운디드 컨텍스트 모듈

각 바운디드 컨텍스트(`contexts/<컨텍스트>/`)는 외부에 계약을 공개하는 `<컨텍스트>-api`와 실제 비즈니스 로직을 구현하는 `<컨텍스트>-core` 두 모듈로 구성됩니다.

### 공개 계약 모듈 (`-api`)

내부 아키텍처(헥사고날, 레이어드)와 무관하게 모든 컨텍스트의 `-api` 모듈은 동일한 패키지 구조(`com.contentria.<컨텍스트>.api`)와 작성 규칙을 따릅니다.

```text
contexts/publishing/publishing-api/src/main/kotlin/com/contentria/publishing/api/
├── GetPostVisibilityQuery.kt    타 컨텍스트나 화면 조합(composition)이 호출하는 공개 포트
├── GetPostSummariesQuery.kt
├── dto/                         공개 포트의 입출력 DTO
└── event/                       이 컨텍스트가 발행하는 도메인 이벤트 페이로드 및 토픽 상수
```

| 작성 규칙                                               | 이유                                                                                              |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 인터페이스와 불변 데이터 클래스(`data class`)만 둡니다. | 향후 마이크로서비스로 분리할 때 이 모듈을 그대로 클라이언트 SDK(라이브러리)로 활용할 수 있습니다. |
| Spring, JPA 등 프레임워크 의존성을 두지 않습니다.       | 순수 Kotlin 계약으로 유지하여 참조하는 쪽에 불필요한 기술 의존성이 전파되지 않게 합니다.          |
| JPA 엔티티나 도메인 모델을 외부로 노출하지 않습니다.    | 영속성 엔티티와 지연 로딩 프록시는 네트워크 경계를 넘어 전달할 수 없습니다.                       |
| 상태 변경(명령) 포트는 원칙적으로 두지 않습니다.        | 타 컨텍스트의 상태 변경을 유발하는 후속 처리는 도메인 이벤트로 비동기 전파하는 것이 원칙입니다.   |

단, 게시글 저장·삭제 시 출판 컨텍스트가 미디어 컨텍스트를 호출해 이미지·영상 연결 상태를 동기화하는 작업은 상태 변경이지만 즉시 반영이 필요하므로 예외적으로 동기 명령 포트를 허용합니다. 이처럼 예외적으로 허용하는 명령 포트는 반드시 [strategic/04-context-map.md](../strategic/04-context-map.md)의 동기 호출 표에 명시합니다.

### 내부 구현 모듈 (`-core`)

`-core` 모듈은 도메인 모델과 유스케이스뿐 아니라 웹 컨트롤러, 이벤트 컨슈머, 배치 잡 같은 인바운드 진입점까지 함께 소유합니다([배경 참고](#컨텍스트-내부의-진입점-소유)). 세부 계층 구조는 컨텍스트가 선택한 아키텍처(헥사고날 또는 레이어드)에 따라 달라지지만([02-context-architecture.md](02-context-architecture.md) 참고), 다음 네 가지 구성 요소는 모든 컨텍스트에 공통으로 존재합니다.

| 구성 요소                        | 위치 및 설명                                                                                                              |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| 채널별 Spring 설정 클래스        | `com.contentria.<컨텍스트>.config` 패키지에 위치하며, `apps/*` 모듈이 필요한 진입 채널만 선택해 `@Import`합니다.          |
| Flyway 마이그레이션 스크립트     | `src/main/resources/db/migration/<컨텍스트>/` 경로에서 해당 컨텍스트 전용 스키마의 DDL을 관리합니다.                      |
| 타 컨텍스트 호출 번역 계층 (ACL) | 헥사고날은 `adapter/required/<상대>/`, 레이어드는 `infra/client/`에 위치합니다.                                           |
| 리포지토리 인터페이스            | Spring Data의 `Repository` 마커 인터페이스를 상속하며, 헥사고날은 `application/required/`, 레이어드는 `domain/`에 둡니다. |

## 실행 조립 모듈

### 프로세스 분리 기준

`apps/*` 모듈은 필요한 컨텍스트들을 조합해 실행 프로세스로 기동하는 역할을 맡습니다. 모듈을 나누는 기준은 인바운드 프로토콜의 종류가 아니라 **독립된 생명주기와 확장 전략을 가져야 하는 운영 프로세스 단위**입니다. 예를 들어 대량 데이터를 처리하는 배치 작업을 API 서버와 같은 프로세스에서 실행하면 메모리 사용량 급증이 API 응답 지연으로 이어지고 독립적인 스케일링도 불가능하므로 별도 프로세스로 분리합니다.

| 모듈       | 실행 형태  | 포함하는 컨텍스트              | 주요 역할 및 운영 특성                                                          |
| ---------- | ---------- | ------------------------------ | ------------------------------------------------------------------------------- |
| `api`      | 상주 서버  | 전체 (6개 컨텍스트)            | HTTP API 제공, Kafka 이벤트 소비, Outbox 릴레이 실행 (무중단 배포 대상)         |
| `batch`    | 일회성 Job | `member`, `media`, `analytics` | 지정된 배치 Job을 1회 실행 후 종료 코드 반환 (Kubernetes CronJob 등으로 실행)   |
| `worker`   | 상주 워커  | `media`                        | Cloudflare Queues에서 업로드 이벤트를 Pull하여 FFmpeg 영상 변환 수행            |
| `migrator` | 일회성 Job | 전체 (마이그레이션 SQL만)      | 배포 시 애플리케이션 기동 전에 컨텍스트별 DB 마이그레이션 수행 (ArgoCD PreSync) |

### 컨텍스트 채널 조립

각 컨텍스트의 `-core` 모듈은 진입 채널(웹, 메시징, 배치, 큐)별로 `@Configuration` 클래스를 제공합니다. 실행 앱(`apps/*`)은 컨텍스트 내부 패키지 구조를 알 필요 없이 자신이 띄울 채널의 설정 클래스만 명시적으로 `@Import`하여 조립합니다.

| 실행 채널                | 설정 클래스 명명 규칙              | `@Import`하는 실행 앱            |
| ------------------------ | ---------------------------------- | -------------------------------- |
| HTTP 웹 진입점           | `<컨텍스트>WebConfiguration`       | `api`                            |
| Kafka 이벤트 소비자      | `<컨텍스트>MessagingConfiguration` | `api`                            |
| Spring Batch 잡          | `<컨텍스트>BatchConfiguration`     | `batch`                          |
| 외부 큐(R2 이벤트) 소비  | `<컨텍스트>QueueConfiguration`     | `worker`                         |
| 도메인 서비스·리포지토리 | `<컨텍스트>CoreConfiguration`      | 해당 컨텍스트를 사용하는 모든 앱 |

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

### 화면 조회 조합

화면 응답을 만들기 위해 여러 컨텍스트의 데이터를 한 번에 조합해야 하는 `composition` 계층과 공통 HTTP 인증 필터는 특정 컨텍스트 하나에 속하지 않으므로 `apps/api` 모듈에 둡니다. 향후 컨텍스트들을 마이크로서비스로 분리할 경우 이 계층이 자연스럽게 BFF(Backend For Frontend) 역할을 맡게 됩니다.

```text
apps/api/src/main/kotlin/com/contentria/app/api/
├── ApiApplication.kt
├── security/                    JWT 인증 필터 및 요청 인가 설정
└── composition/                 여러 컨텍스트의 -api를 조합하는 화면 전용 조회 계층
    ├── PostDetailQuery.kt
    └── BlogDashboardQuery.kt
```

## 설계 배경

### 바운디드 컨텍스트 기준 분할

초기에는 최상위 모듈을 기술 계층에 따라 `core`(도메인 및 아웃바운드 어댑터 전체)와 `app`(인바운드 웹 어댑터)으로 나누고, 각 바운디드 컨텍스트는 `core` 모듈 내부의 패키지로만 구분했습니다. 그러나 모든 컨텍스트가 단일 모듈 안에 공존하다 보니 다른 컨텍스트의 내부 도메인 클래스를 직접 참조해도 빌드가 그대로 통과했고, 컨텍스트별로 적합한 내부 아키텍처(헥사고날 또는 레이어드)를 다르게 선택할 수도 없었습니다.

최상위 분할 축을 바운디드 컨텍스트로 전환함으로써 컴파일러 수준에서 컨텍스트 간 경계를 강제하고, 각 컨텍스트가 자신의 복잡도에 맞는 내부 구조를 독립적으로 선택할 수 있게 했습니다.

### 컨텍스트 내부의 진입점 소유

과거에는 모든 웹 컨트롤러를 `app` 모듈에 모아 두었으나, 이 구조에서는 특정 컨텍스트를 별도 서비스로 분리할 때 `core`와 `app` 양쪽에서 관련 코드를 일일이 발라내야 했습니다.

컨트롤러, 이벤트 컨슈머, 배치 잡 같은 인바운드 진입점을 각 컨텍스트의 `-core` 모듈 내부로 이동시켜, 향후 해당 컨텍스트 디렉터리만 그대로 떼어내면 독립 서비스로 기동할 수 있도록 구성했습니다. 결과적으로 `apps/*` 모듈에는 프로세스 조립, 공통 인증 필터, 화면 조회 조합(`composition`)처럼 특정 컨텍스트 하나에 귀속되지 않는 요소만 남습니다.

### 마이크로서비스 분리 시 변경 범위

바운디드 컨텍스트 단위로 모듈과 진입점을 격리해 두었기 때문에, 향후 특정 컨텍스트를 독립된 마이크로서비스로 분리할 때 변경 범위가 해당 컨텍스트 내부와 조립 설정으로 국한됩니다. 예를 들어 `notification`(알림) 컨텍스트를 분리할 때의 변경 범위는 다음과 같습니다.

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
| 비동기 도메인 이벤트       | 모듈러 모놀리스 단계에서도 이미 Kafka로 이벤트를 주고받으므로 변경하지 않습니다.           |
| `notification-api` 참조처  | 모노레포 외부로 분리하는 경우에만 Gradle 프로젝트 의존성을 아티팩트 의존성으로 변경합니다. |
