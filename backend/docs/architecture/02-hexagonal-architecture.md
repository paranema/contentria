# 헥사고날 아키텍처

## 레이어 정의

| 레이어 | 위치 | 역할 |
|---|---|---|
| domain | `core/{context}/domain` | 비즈니스 규칙, 애그리거트, 값 객체 |
| application | `core/{context}/application` | 유스케이스 조율, 트랜잭션 경계, 포트 정의 |
| adapter (out) | `core/{context}/adapter/out` | 영속성, 외부 API 호출 |
| adapter (in) | `app/{module}/{context}` | HTTP, 배치 잡, 메시지 소비 |

## 패키지 구조

### core 모듈

```
core/src/main/kotlin/com/contentria/
├── common/
│   ├── persistence/         BaseEntity, 감사 설정
│   ├── id/                  식별자 생성
│   └── exception/           공통 예외 계층
│
├── member/
│   ├── domain/
│   │   ├── Account.kt
│   │   ├── Credential.kt
│   │   ├── AccountStatus.kt
│   │   └── Profile.kt
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/          유스케이스 인터페이스, 커맨드, 결과 DTO
│   │   │   └── out/         SPI 인터페이스
│   │   └── service/         유스케이스 구현
│   └── adapter/
│       └── out/
│           ├── persistence/ JpaRepository, PersistenceAdapter
│           └── crypto/      PasswordHasher 구현
│
├── publishing/
├── interaction/
├── media/
├── analytics/
└── notification/
```

컨텍스트 목록은 [strategic/03-bounded-contexts.md](../strategic/03-bounded-contexts.md)를 따른다.

`in`은 Kotlin 키워드이므로 코드에서 백틱으로 감싼다.

```kotlin
package com.contentria.account.application.port.`in`
```

### app 하위 모듈

`app/api` 모듈 전체가 이미 인바운드 어댑터이므로 `adapter/in`을 다시 쓰지 않는다.
컨텍스트 축만 유지한다.

```
app/api/src/main/kotlin/com/contentria/api/
├── ApiApplication.kt
├── config/
├── security/                SecurityConfig, JWT 필터
├── member/
│   ├── AccountController.kt
│   └── dto/
└── publishing/
```

```
app/batch/src/main/kotlin/com/contentria/batch/
├── BatchApplication.kt
└── post/
    └── PostArchiveJobConfig.kt
```

패키지는 `com.contentria.api.post` 순서로 짓는다.
`com.contentria.post.api`로 하면 `core`와 같은 패키지 트리를 서로 다른 jar가 공유하게 된다.

## 의존 방향

```
app/api ─┐
app/batch ├─→ core.{context}.application.port.in
app/worker ┘          │
                      ↓
              core.{context}.application.service
                      │
                      ↓
              core.{context}.domain
                      ↑
              core.{context}.adapter.out (포트 구현)
```

규칙:

1. `domain`은 아무것도 의존하지 않는다. 예외적으로 JPA 애노테이션만 허용한다(04 문서 참고).
2. `application`은 `domain`만 의존한다.
3. `adapter`는 `application`의 포트를 구현하거나 호출한다.
4. `app` 하위 모듈은 `core`의 `port/in`만 호출한다. `service` 구현체를 직접 주입받지 않는다.

## 포트 명명

기술 용어가 아니라 유스케이스 언어를 쓴다.

```kotlin
// 권장
interface LoadAccountPort
interface SaveAccountPort
interface PasswordHasher

// 비권장
interface AccountRepository
```

인바운드 포트는 동사로 시작한다.

```kotlin
interface RegisterAccountUseCase
interface AuthenticateUseCase
interface GetProfileQuery      // 조회는 Query 접미사
```

## 경계에 엔티티를 노출하지 않는다

`port/in` 시그니처에는 DTO만 쓴다.

```kotlin
// 권장
data class AuthenticatedAccount(
    val accountId: UUID,
    val email: String,
    val roles: Set<String>,
)

// 비권장: 엔티티를 그대로 반환
fun authenticate(email: String, password: String): Account
```

엔티티를 반환하면 지연 로딩 프록시가 웹 레이어까지 나가고,
JSON 직렬화 시점에 컬렉션이 전부 로딩된다.

## ArchUnit

```kotlin
@AnalyzeClasses(packages = ["com.contentria"])
class ArchitectureTest {

    @ArchTest
    val 도메인은_웹과_시큐리티를_모른다: ArchRule =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.security.core.context..",
                "org.springframework.data..",
            )

    @ArchTest
    val 안쪽은_바깥쪽을_모른다: ArchRule =
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")

    @ArchTest
    val 컨텍스트는_순환하지_않는다: ArchRule =
        slices().matching("com.contentria.(*)..").should().beFreeOfCycles()
}
```

`app` 모듈에 대한 규칙은 각 모듈의 테스트에 둔다.

```kotlin
@ArchTest
val app은_서비스_구현체를_모른다: ArchRule =
    noClasses().that().resideInAPackage("com.contentria.api..")
        .should().dependOnClassesThat()
        .resideInAPackage("..application.service..")
```

이 규칙이 실질적으로 중요하다.
컨트롤러가 서비스 구현체를 직접 주입받기 시작하면 포트가 형식적인 존재가 된다.