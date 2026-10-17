# 바운디드 컨텍스트

## 목록

| 컨텍스트 | 애그리거트 루트 | 담당 |
|---|---|---|
| account | Account (멤버: Credential, Role) | 계정, 자격증명, 권한 |
| profile | Profile | 공개 프로필(표시 이름, 이미지) |
| blog | Blog | 블로그, 슬러그 |
| post | Post | 게시글, 카테고리 |
| comment | Comment | 댓글 |
| media | Media | 이미지, 파일 |
| video | Video | 영상 |
| subscription | Subscriber | 구독, 플랜 |
| notification | Notification | 알림 발송 |

## 컨텍스트가 아닌 것

| 항목 | 처리 |
|---|---|
| dashboard | 여러 컨텍스트의 조회 결과를 조합하는 읽기 모델. `app/api`에 둔다 |
| analytics | 위와 동일 |
| auth | HTTP 인증 관심사는 `app/api/security`, 도메인 규칙은 `account`로 분리 |
| global | 성격별로 분해. 대부분 `core/common` 또는 `app/api/config` |

`dashboard`와 `analytics`를 `core`의 컨텍스트로 만들면
`dashboard → post`, `dashboard → account` 의존이 생겨 컨텍스트 분리를 막는다.
조회 전용 코드는 각 컨텍스트의 인바운드 포트를 조합하는 방식으로 둔다.

## account와 profile을 나눈 이유

접근 패턴이 다르다.

| | account | profile |
|---|---|---|
| 담는 것 | email, status, password, provider, role | 표시 이름, 프로필 이미지 |
| 민감도 | 높음 | 공개 |
| 조회 빈도 | 인증 시점만 | post, comment 등에서 상시 |
| 읽는 주체 | 자기 자신 | 다수 컨텍스트 |

`post`가 필요한 것은 작성자의 표시 이름과 이미지뿐이다.
하나의 테이블에 두면 조회할 때 email과 status까지 딸려 나온다.

## Account와 Credential

Account는 "누구인가", Credential은 "어떻게 증명하는가"를 담당한다.

| | Account | Credential |
|---|---|---|
| 개수 | 계정당 1개 | 계정당 1개 이상 |
| 정체성 | 있음. 다른 컨텍스트가 참조하는 ID | 없음. 계정에 종속 |
| 필드 | email, status, roles | provider, provider_id, password_hash |
| 삭제 시 | 계정 소멸 | 연동 해제. 계정은 유지 |

email은 계정의 연락 수단이므로 Account가 소유한다.
Credential에 email을 두지 않는다. OAuth 자격증명에는 비밀번호가 없듯이,
email도 자격증명의 속성이 아니다.

## 컨텍스트 간 통신

### 규칙

1. 상대 컨텍스트의 `port/in`만 호출한다. `domain`, `service` 구현체, Repository를 직접 참조하지 않는다.
2. 호출은 자기 컨텍스트의 `port/out`을 정의하고 `adapter/out`에서 구현한다.
3. 타입 변환은 어댑터에서 한다. 자기 도메인 코드에 상대 컨텍스트의 타입이 등장하지 않는다.

### 예시

```kotlin
// core/post/application/port/out/LoadAuthorPort.kt
// post가 자기 언어로 정의한다. profile 패키지를 모른다.
interface LoadAuthorPort {
    fun loadAll(authorIds: Set<AuthorId>): Map<AuthorId, AuthorView>
}

// core/post/domain/AuthorView.kt
// post가 필요한 것만 담는다.
data class AuthorView(
    val id: AuthorId,
    val displayName: String,
    val pictureUrl: String?,
)

// core/post/adapter/out/profile/ProfileAuthorAdapter.kt
// 유일하게 profile을 참조하는 지점.
@Component
class ProfileAuthorAdapter(
    private val getProfileQuery: GetProfileQuery,
) : LoadAuthorPort { ... }
```

이 구조에서 profile을 별도 서비스로 분리할 때
`ProfileAuthorAdapter` 내부만 HTTP 호출로 바꾸면 된다. 호출부는 수정하지 않는다.

### 조회 성능

목록 화면에서 게시글과 작성자 정보를 함께 보여줘야 하면 N+1이 발생할 수 있다.
현재는 배치 조회(`loadAll`)로 해결한다.

성능이 실제로 문제가 되면 다음을 검토한다.

- 스냅샷 저장: `post` 테이블에 작성 시점의 표시 이름을 함께 저장
- 읽기 전용 모델 분리: 조회 전용 쿼리로 컨텍스트 규칙을 우회

측정 전에 도입하지 않는다.

## 컨텍스트 간 참조 금지 사항

### JPA 연관관계

```kotlin
// 금지
@ManyToOne
val author: Account

// 권장
@Column(name = "author_id")
val authorId: UUID
```

DB에 FK 제약을 거는 것과 코드에 연관관계를 매핑하는 것은 다른 문제다.
전자는 무결성 장치이고 후자는 결합이다.
모놀리스 단계에서 FK는 유지해도 되지만 연관관계 매핑은 하지 않는다.

### 트랜잭션

컨텍스트 간 작업이 하나의 `@Transactional`로 묶이면 분리가 불가능해진다.
정합성이 즉시 필요하지 않은 경우 이벤트로 전환한다.

```kotlin
applicationEventPublisher.publishEvent(AccountRegisteredEvent(accountId))

@TransactionalEventListener(phase = AFTER_COMMIT)
fun on(event: AccountRegisteredEvent) { ... }
```

## 컨텍스트 추가 기준

새 개념이 나왔을 때 바로 컨텍스트를 만들지 않는다. 다음을 확인한다.

- 고유한 애그리거트 루트와 불변식이 있는가
- 다른 컨텍스트 없이 독립적으로 의미가 있는가
- 트랜잭션 경계가 다른가

셋 다 아니면 기존 컨텍스트의 하위 개념이다.
예: `category`는 `post` 안의 하위 개념일 가능성이 높다.