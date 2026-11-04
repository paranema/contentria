# 보안 규약

인증 토큰, 세션 관리, 쿠키 정책 및 입력 검증 등 백엔드 전반에 적용되는 보안 규약을 정의합니다.

> [!NOTE]
> 회원 컨텍스트 고유의 비즈니스 규칙(비밀번호 정책, 사람 확인, 로그인 실패 정책)은 [contexts/member/01-rules.md](../contexts/member/01-rules.md)를 참고합니다.

## 인증 토큰 체계

### 토큰 분류

| 토큰 종류 | 형태 | 저장 위치 | 유효 기간 | 검증 방식 |
|---|---|---|---|---|
| 액세스 토큰 | HS256 서명 JWT | HttpOnly 쿠키 `accessToken`<br>(서버 간 호출은 `Authorization` 헤더) | 15분 (`contentria.member.access-token.ttl`) | DB 조회 없이 서명 및 만료 시간만 검증 |
| 리프레시 토큰 | 난수 문자열 (정보 미포함) | HttpOnly 쿠키 `refreshToken`<br>(DB에는 SHA-256 해시값만 저장) | 7일 (`contentria.member.refresh-token-ttl`) | DB에서 해시값 일치 여부 조회 |

쿠키 이름 상수는 `SessionCookieNames`에 정의되어 있습니다.

### 이중 토큰 채택 배경

| 토큰 | 채택 이유 |
|---|---|
| 액세스 토큰 | 요청마다 데이터베이스를 조회하지 않아 성능 부하를 줄입니다. 탈취 피해를 최소화하기 위해 수명을 15분으로 짧게 유지합니다. |
| 리프레시 토큰 | 서버의 데이터베이스에서 상태를 제어할 수 있어 즉시 세션을 종료할 수 있습니다. 갱신 시마다 새 값으로 교체하여 탈취 여부를 감지합니다. |

리프레시 토큰은 데이터베이스에 SHA-256 해시값으로만 보관하므로, DB 데이터가 유출되더라도 토큰 원문으로 위장할 수 없습니다.

## 세션 생명주기

하나의 계정은 기기마다 독립적인 세션을 가집니다. 하나의 세션은 하나의 리프레시 토큰에 대응합니다.

| 작업 | 처리 방식 |
|---|---|
| 단일 로그아웃 | 요청한 기기의 리프레시 토큰만 삭제합니다. 다른 기기의 세션은 유지됩니다. |
| 전체 세션 무효화 | 비밀번호 변경·재설정, 연동 해제, 이메일 변경 확정 시 해당 계정의 모든 리프레시 토큰을 삭제하고, 현재 요청한 기기에만 신규 세션을 발급합니다. |
| 회원 탈퇴 | 계정의 모든 리프레시 토큰을 즉시 삭제하고 브라우저 세션 쿠키를 만료시킵니다. |
| 기발급 액세스 토큰 | 무상태(Stateless) JWT 특성상 이미 발급된 토큰은 최대 15분 만료 시점까지 유효합니다. |

## 토큰 갱신 절차

토큰 갱신(`POST /auth/refresh`, `SessionService.refresh`)은 리프레시 토큰 회전(Refresh Token Rotation, RTR) 원칙을 따릅니다.

1. **토큰 조회**: 전달받은 토큰의 SHA-256 해시로 DB를 조회합니다. 일치하는 데이터가 없으면 즉시 거부합니다.
2. **기존 토큰 삭제**: 확인된 기존 리프레시 토큰 레코드를 즉시 삭제합니다.
3. **상태 검증**: 토큰 만료 여부 및 계정 상태(정지 등)를 검증합니다.
4. **신규 발급**: 새 액세스 토큰과 새 리프레시 토큰을 동시에 발급합니다.

한 번 사용된 리프레시 토큰은 DB에서 즉시 제거되므로 재사용할 수 없습니다. 탈취된 토큰이 있더라도 정상 사용자가 먼저 갱신하면 공격자의 토큰은 무효화됩니다.

동일 토큰으로 동시에 갱신 요청이 들어올 경우 하나의 요청만 성공합니다. 상세 동작 및 경쟁 상태 방어 배경은 [design-notes/refresh-token-grace-window.md](../design-notes/refresh-token-grace-window.md)를 참고합니다.

## 쿠키 보안 정책

### 속성 규칙

세션 쿠키의 속성은 백엔드가 일괄 결정합니다 (`SessionCookies`).

| 속성 | 설정값 | 설정 이유 |
|---|---|---|
| `HttpOnly` | 활성화 (`true`) | 자바스크립트의 쿠키 접근을 차단하여 XSS를 통한 토큰 탈취를 방지합니다. |
| `Secure` | 환경 설정값 (`contentria.member.cookie.secure`) | HTTPS 통신에서만 쿠키를 전송합니다. (로컬 개발 환경에서만 비활성화) |
| `SameSite` | `Lax` | 교차 사이트 요청 위조(CSRF) 공격을 방어합니다. |
| `Path` | `/` | 도메인 전체 경로에서 세션 쿠키를 공유합니다. |
| `Max-Age` | 토큰별 잔여 수명 | 토큰 만료 시 브라우저에서도 쿠키가 자동 파기되도록 설정합니다. |

로그아웃 및 탈퇴 시에는 동일한 속성에 `Max-Age=0`을 부여하여 브라우저에서 쿠키를 삭제합니다.

> [!WARNING]
> **Secure 속성은 HTTP 요청 정보로 결정하지 않음**
> 리버스 프록시 뒤에 위치한 백엔드는 HTTP 요청을 전달받을 수 있으므로, `request.isSecure` 대신 항상 프로퍼티 설정값(`properties.cookie.secure`)을 직접 참조합니다.

### SameSite 정책

`SameSite`는 브라우저가 다른 사이트에서 시작된 요청에 쿠키를 포함할지 결정하는 브라우저 레벨의 CSRF 방어선입니다.

- `Strict`: 동일 사이트 요청에만 쿠키를 포함합니다. 타 사이트 링크를 통한 진입 시 쿠키가 전송되지 않습니다.
- `Lax`: 동일 사이트 요청 및 타 사이트에서 시작된 최상위 탐색(링크 클릭 등 GET 이동)에 쿠키를 포함합니다. 외부 사이트의 POST, iframe, AJAX 요청에는 쿠키를 전송하지 않습니다.
- `None`: 모든 교차 사이트 요청에 쿠키를 전송하며, `Secure` 속성이 필수입니다.

백엔드 API는 서버 세션을 유지하지 않으므로 Spring Security의 CSRF 방어 필터를 비활성화합니다 (`csrf { it.disable() }`). 따라서 **`SameSite=Lax` 쿠키 설정이 CSRF에 대한 핵심 방어선**입니다. 외부 GET 이동 시 쿠키가 전달될 수 있으므로, 상태를 변경하는 모든 API는 반드시 POST, PUT, DELETE 메서드로 설계합니다.

## 요청 전달 흐름

### 쿠키 중계 흐름

```
브라우저 ──(1) 쿠키──> Next.js 서버 (proxy.ts, Server Actions) ──(2) 헤더──> 백엔드 API
```

1. **브라우저 → Next.js**: 브라우저가 SameSite 규칙에 따라 세션 쿠키를 전송합니다.
2. **Next.js → 백엔드 API**: 서버 간 통신 구간입니다. Next.js가 액세스 토큰을 `Authorization: Bearer <token>` 헤더로 변환하여 전달합니다. 리프레시 토큰은 토큰 갱신 및 로그아웃 요청 시에만 `Cookie` 헤더로 전달합니다.

### 소셜 로그인 인증 흐름

구글 OAuth2 인증은 프론트엔드 프록시를 거치지 않고 백엔드 엔드포인트로 직접 복귀합니다.

```
브라우저 ──> 구글 인증 ──> 백엔드 (/login/oauth2/code/google)
                                ├── 세션 쿠키 2종 발급 (Set-Cookie)
                                └── 프론트엔드로 302 리다이렉트
```

## 입력값 검증

### 검증 규칙

| 검증 영역 | 규칙 |
|---|---|
| 요청 본문 | 컨트롤러 요청 DTO에 `@Valid`를 명시하고, 필드 단위로 Bean Validation(`@field:NotBlank`, `@field:Size` 등)을 선언합니다. |
| 리소스 ID | URI 경로 변수(`@PathVariable`)로 단일화합니다. 경로로 전달받은 ID를 본문 DTO에서 중복해서 받지 않습니다. |
| 인증 주체 | `@AuthenticationPrincipal`을 통해 컨텍스트 인증 정보에서 직접 추출합니다. 쿼리 스트링이나 요청 본문으로 계정 ID를 받지 않습니다. |

### 리소스 소유권 확인

데이터 변경 요청 시 애플리케이션 서비스 계층에서 엔티티를 조회하여 요청자와 리소스 소유자가 일치하는지 반드시 검증합니다.

```kotlin
val blog = blogs.findBySlug(command.blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
if (!blog.isOwnedBy(command.requesterId)) throw PublishingUseCaseException.NotBlogOwner()
```

## 비밀번호 보안

계정 인증 수단은 비밀번호, 이메일 인증 코드, 소셜 로그인을 지원합니다. 비밀번호 없이 가입한 소셜 계정이 존재하므로 자격 증명 테이블의 비밀번호 해시값은 null일 수 있습니다.

| 원칙 | 구현 지침 |
|---|---|
| 단방향 암호화 | `BCryptPasswordHasher`를 사용해 BCrypt 알고리즘으로 단방향 해싱합니다. |
| 민감정보 보호 | 비밀번호 원문과 해시값은 절대 로그에 남기지 않습니다. ([logging.md](logging.md) 참고) |
| 미설정 계정 방어 | DB의 비밀번호 해시가 비어 있는 경우, 암호 대조를 수행하지 않고 즉시 인증 실패(`false`)로 처리합니다. |

## 예외 처리 규약

### 오류 응답 형식

각 바운디드 컨텍스트의 `*WebExceptionHandler`는 예외를 일관된 `ErrorResponse(code, message)` 형식으로 반환합니다.
오류 메시지는 정형화된 사용자 안내 문구로 제한하며, 데이터베이스 컬럼명, SQL 쿼리, 내부 파라미터 값 등 시스템 세부 정보를 노출하지 않습니다.

```kotlin
// 올바른 형식: 사전 정의된 코드와 정형 메시지
error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password does not match.")

// 잘못된 형식: 내부 구현 및 민감정보 노출
"User not found in credentials table for email: john@example.com"
```

로그인 실패 시 계정 미존재와 비밀번호 불일치를 구분하여 안내하지 않습니다.

### 소셜 로그인 실패 처리

소셜 로그인 콜백 처리 중 실패가 발생하여 프론트엔드로 리다이렉트할 때는 예외 메시지를 URL 파라미터로 직접 전달하지 않고, 사전 정의된 오류 코드만 전달합니다 (`SOCIAL_EMAIL_MISSING`, `SOCIAL_EMAIL_NOT_VERIFIED` 등).

```kotlin
// 안전한 방식: 사전 정의된 정적 코드 전달
val url = "https://example.com/login?error=SOCIAL_EMAIL_MISSING"

// 위험한 방식: 예외 메시지 노출 (반사형 XSS 및 오픈 리다이렉트 위험)
val url = "https://example.com/login?error=${exception.message}"
```

## 속도 제한 현황

현재 API 레벨의 속도 제한(Rate Limiting)은 구현되어 있지 않습니다.
무차별 대입 공격(Brute Force) 및 이메일 발송 남용에 대한 방어는 reCAPTCHA v3 사람 확인에 의존하고 있습니다. 상세 보류 현황은 [contexts/member/02-implementation.md](../contexts/member/02-implementation.md)를 참고합니다.
