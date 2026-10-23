# 보안 규약

인증, 쿠키, 토큰, 민감한 정보를 다루는 규약이다.
회원 컨텍스트의 제품 규칙(세션 무효화, 사람 확인, 로그인 실패 응답)은 [contexts/member/01-rules.md](../contexts/member/01-rules.md)에, 그 배치는 [02-implementation.md](../contexts/member/02-implementation.md)에 있다.

## 토큰

| 토큰 | 형태 | 보관 | 수명 | 검증 |
|---|---|---|---|---|
| 액세스 토큰 | HS256으로 서명한 JWT | HttpOnly 쿠키 `accessToken`. 서버 간 호출에서는 `Authorization` 헤더 | 15분(`contentria.member.access-token.ttl`) | 서명만 본다. DB를 읽지 않는다 |
| 리프레시 토큰 | 무작위 값. 아무 정보도 담지 않는다 | HttpOnly 쿠키 `refreshToken`. DB에는 SHA-256 해시만 둔다 | 7일(`contentria.member.refresh-token-ttl`) | DB에서 해시로 찾는다 |

쿠키 이름은 `SessionCookieNames`에 있다.

### 두 가지를 쓰는 이유

| 토큰 | 이유 |
|---|---|
| 액세스 토큰(JWT) | 요청마다 DB를 읽지 않고 서명으로 확인한다. 새어 나가도 피해가 짧도록 수명을 짧게 둔다 |
| 리프레시 토큰(무작위 값) | DB에 있으므로 서버가 지워서 끝낼 수 있다. 쓸 때마다 바꿔 도난을 드러낸다 |

DB에 원값을 두지 않으므로 DB가 새도 리프레시 토큰을 쓸 수 없다.

### 세션

한 계정은 기기마다 세션을 가진다. 세션 하나가 리프레시 토큰 하나다.

| 일 | 처리 |
|---|---|
| 로그아웃 | 그 리프레시 토큰 하나만 지운다. 다른 기기는 로그인 상태로 남는다 |
| 세션 무효화(비밀번호 설정·변경·재설정, 연동 해제, 이메일 변경 확정) | 그 계정의 리프레시 토큰을 모두 지우고, 요청한 브라우저에만 새 세션을 준다 |
| 탈퇴 | 리프레시 토큰을 모두 지우고 쿠키를 만료시킨다 |
| 이미 발급한 액세스 토큰 | 만료(최대 15분)까지 통과한다. 지울 방법이 없다 |

## 요청 인증

`apps/api`의 `AccessTokenAuthenticationFilter`가 `Authorization` 헤더를 먼저 보고, 없으면 `accessToken` 쿠키를 본다.
세션은 서버에 두지 않는다(`SessionCreationPolicy.STATELESS`).

## 리프레시 토큰 회전

`POST /auth/refresh`(`SessionService.refresh`)는 다음 순서로 동작한다.

| # | 처리 |
|---|---|
| 1 | 받은 토큰의 해시로 찾는다. 없으면 거부한다 |
| 2 | 찾은 토큰을 지운다 |
| 3 | 만료됐거나 계정이 로그인할 수 없는 상태면 거부한다 |
| 4 | 새 액세스 토큰과 새 리프레시 토큰을 발급한다 |

이미 회전한 토큰은 DB에 없으므로 거부된다. 훔친 토큰은 원래 주인이 먼저 갱신하면 쓸 수 없다.

같은 토큰으로 동시에 갱신하면 하나만 성공한다. 유예 구간은 없다. 프론트엔드의 `proxy.ts`가 같은 토큰의 동시 갱신을 한 번으로 묶는다. 경위와 남은 위험은 [design-notes/refresh-token-grace-window.md](../design-notes/refresh-token-grace-window.md)에 있다.

## 쿠키

세션 쿠키의 속성은 백엔드가 정한다(`SessionCookies`). 프론트엔드는 백엔드가 보낸 `Set-Cookie`를 속성 그대로 브라우저에 옮길 뿐, 속성을 새로 정하지 않는다.

| 속성 | 값 | 이유 |
|---|---|---|
| `HttpOnly` | 켠다 | 자바스크립트가 읽지 못한다. XSS로 토큰을 훔치지 못하게 한다 |
| `Secure` | 설정값(`contentria.member.cookie.secure`, 기본 켬) | HTTPS로만 보낸다. 로컬 개발에서만 끈다 |
| `SameSite` | `Lax` | CSRF를 막는다. 아래 절 참고 |
| `Path` | `/` | |
| `Max-Age` | 토큰의 남은 수명 | 토큰과 함께 만료된다 |

로그아웃과 탈퇴는 같은 속성에 `Max-Age=0`인 쿠키를 보내 브라우저에서 지운다.

### Secure는 요청에서 정하지 않는다

```kotlin
// 위험: 요청을 보고 정한다
secure = request.isSecure
// 리버스 프록시 뒤에서는 백엔드가 HTTP 요청을 받는다. 프록시 설정이 하나만 틀려도 Secure가 빠진다

// 안전: 환경 설정에서 정한다
secure = properties.cookie.secure
```

### SameSite

`SameSite`는 다른 사이트에서 시작한 요청에 브라우저가 쿠키를 붙일지 정하는 속성이다. 브라우저가 지키는 CSRF 방어다.

| 값 | 동작 |
|---|---|
| `Strict` | 같은 사이트의 요청에만 붙인다. 다른 사이트의 링크로 들어올 때도 붙이지 않는다 |
| `Lax` | 같은 사이트의 요청과, 다른 사이트에서 오는 최상위 GET 이동에 붙인다. 다른 사이트의 POST, iframe, fetch에는 붙이지 않는다 |
| `None` | 언제나 붙인다. `Secure`가 있어야 한다 |

`Lax`를 쓴다. `Strict`는 소셜 로그인을 깨뜨린다. 구글에서 돌아오는 이동은 다른 사이트에서 시작한 것이라, 백엔드가 프론트엔드로 보낸 직후의 첫 화면에 쿠키가 붙지 않는다.

속성을 비워 두면 최신 브라우저는 `Lax`로 다루지만, 오래된 브라우저는 제한 없이 보낸다. 또 코드를 읽는 사람이 일부러 비운 것인지 잊은 것인지 알 수 없다. 언제나 명시한다.

#### 이 프로젝트에서 SameSite가 중요한 이유

`apps/api`는 Spring Security의 CSRF 보호를 끈다(`SecurityConfiguration`의 `csrf { it.disable() }`). 서버에 세션을 두지 않고, 브라우저가 저절로 보내는 인증 수단은 `SameSite=Lax` 쿠키뿐이기 때문이다.

**따라서 `SameSite`가 유일한 CSRF 방어다.** 빠지면 CSRF에 그대로 열린다.
`Lax`는 다른 사이트에서 오는 GET 이동에는 쿠키를 붙이므로, 상태를 바꾸는 API를 GET으로 만들지 않는다.

### 요청 경로와 쿠키

```
브라우저 ──(1)──> Next.js 서버 (proxy.ts, Server Actions) ──(2)──> 백엔드 API
```

| 구간 | SameSite | 쿠키를 다루는 방식 |
|---|---|---|
| (1) 브라우저 → Next.js | 브라우저가 적용한다. 지켜야 하는 경계다 | 브라우저가 세션 쿠키를 붙인다 |
| (2) Next.js → 백엔드 | 서버 간 호출이라 상관없다 | Next.js가 액세스 토큰을 `Authorization` 헤더로 붙인다. 리프레시 토큰은 갱신과 로그아웃 때만 `Cookie` 헤더로 보낸다 |

백엔드가 쿠키를 심으면 Next.js가 그 `Set-Cookie`를 브라우저 응답으로 옮긴다.

| 위치 | 하는 일 | 파일 |
|---|---|---|
| 백엔드 | 로그인, 가입, 갱신, 세션 무효화, 소셜 로그인 콜백의 응답에 쿠키를 심는다 | `SessionCookies.kt` |
| 프론트엔드 proxy | 화면을 옮길 때 갱신하고, 받은 두 쿠키를 브라우저 응답에 심는다 | `frontend/src/proxy.ts` |
| 프론트엔드 Server Action | 로그인, 가입, 로그아웃 응답의 쿠키를 브라우저로 옮긴다 | `frontend/src/shared/api/sessionCookies.ts`, 해석은 `setCookie.ts` |

### 소셜 로그인

구글 로그인은 Next.js를 거치지 않는다. 브라우저가 구글에서 백엔드로 바로 돌아온다.

```
브라우저 ──> 구글 ──> 백엔드 (/login/oauth2/code/google)
                          ├── 세션 쿠키 두 개를 심는다
                          └── 프론트엔드로 302
```

## 오류 응답

컨텍스트마다 `*WebExceptionHandler`가 실패를 `ErrorResponse(code, message)`로 바꾼다. 메시지는 일반적인 문장으로 쓰고, 클라이언트가 보낸 값이나 내부 구현(스택 트레이스, SQL, 테이블 이름)을 담지 않는다.

```kotlin
// 좋다: 정해 둔 코드와 일반적인 문장
error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password does not match.")

// 나쁘다: 내부 구현과 받은 값을 드러낸다
"User not found in credentials table for email: john@example.com"
```

로그인 실패는 미가입인지 비밀번호 오류인지 구분하지 않는다(회원 규칙).

## 소셜 로그인 실패

실패를 프론트엔드 주소로 돌려보낼 때 예외 메시지를 넣지 않는다. 정해 둔 코드만 넣는다(`SocialLoginHandlers`의 `SOCIAL_EMAIL_MISSING`, `SOCIAL_EMAIL_NOT_VERIFIED` 등).

```kotlin
// 위험: 예외 메시지에 무엇이 들어 있을지 모른다
val url = "https://example.com/login?error=${exception.message}"

// 안전: 정해 둔 코드
val url = "https://example.com/login?error=SOCIAL_EMAIL_MISSING"
```

예외 메시지를 주소에 넣으면 내부 정보가 새고, 반사형 XSS나 열린 리디렉션의 재료가 된다.

## 입력 검증

| 대상 | 규칙 |
|---|---|
| 요청 본문 | 컨트롤러에서 `@Valid`를 붙이고, 필드에 `@field:NotBlank`, `@field:Size`, `@field:Pattern` 같은 제약을 둔다 |
| 대상 ID | 경로에서 받는다. 경로로 받은 ID를 본문에서 다시 받지 않는다 |
| 요청한 회원 | `@AuthenticationPrincipal`로 받는다. 본문이나 쿼리로 받지 않는다 |

### 소유 확인

쓰기 요청은 애플리케이션 서비스가 대상을 읽어 소유자를 확인한다.

```kotlin
// PostService: 글을 쓰기 전에 블로그 소유자를 확인한다
val blog = blogs.findBySlug(command.blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
if (!blog.isOwnedBy(command.requesterId)) throw PublishingUseCaseException.NotBlogOwner()
```

경로의 ID와 본문의 ID가 함께 있으면, 본문의 ID로 남의 자원을 바꿀 수 있다. 그래서 같은 ID를 두 곳에서 받지 않는다.

## 비밀번호

로그인 방법은 비밀번호, 이메일 코드, 구글 세 가지다. 비밀번호 없이 가입한 계정이 있으므로 `Credential`의 비밀번호 해시는 비어 있을 수 있다. 버그가 아니다.

| 규칙 | 코드 |
|---|---|
| BCrypt로 해시한다 | `BCryptPasswordHasher` |
| 원문과 해시를 로그에 남기지 않는다 | [logging.md](logging.md) |
| 해시가 비어 있으면 대조하지 않고 실패로 본다 | `Credential.matchesPassword`가 `false`를 돌려준다 |

해시가 없는 계정이 비밀번호 대조를 통과하는 일을 막고, 인코더에 빈 값을 넘기지 않는다.

## 속도 제한

지금은 없다. 회원 규칙은 "실패한 요청도 한도를 소비하고, IP와 계정·이메일을 키로 쓴다"고 정했지만, 구현은 범위 밖으로 두었다([contexts/member/02-implementation.md](../contexts/member/02-implementation.md)의 "아직 없는 것"). 업로드 요청 수 제한도 보류다([contexts/media/01-rules.md](../contexts/media/01-rules.md)의 "보류").

`gradle/libs.versions.toml`에 Bucket4j와 Caffeine JCache 항목이 있지만 어느 모듈도 쓰지 않는다. 레거시는 인증 경로에 IP당 분당 10회 제한을 두었다.

지금 대입 공격과 코드 메일 폭주를 막는 것은 사람 확인(reCAPTCHA)뿐이다.
