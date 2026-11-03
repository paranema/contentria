# 회원 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 회원가입, 로그인, 소셜 연동, 계정 보안 설정 및 프로필 관리 규칙을 실제 코드 계층과 데이터베이스에 어떻게 배치해 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

회원 컨텍스트(`contexts/member`)는 OAuth2/OIDC, 메일 발송, reCAPTCHA 검증 등 외부 연동으로부터 도메인 모델을 격리하기 위해 [architecture/02-context-architecture.md](../../architecture/02-context-architecture.md)의 **헥사고날 아키텍처**를 따릅니다.

단일 애그리거트 내부 상태만으로 판단할 수 있으면 도메인 불변식(`도메인`)이고, 다른 애그리거트나 외부 저장소를 조회해야 판단할 수 있으면 애플리케이션 정책(`앱`)입니다. 예를 들어 `Account` 엔티티 객체 하나만으로는 "이 이메일을 사용하는 다른 계정이 이미 존재하는가?"를 알 수 없으므로, 중복 검사와 같은 규칙은 **애플리케이션 사전 조회**와 **데이터베이스 고유 제약(`UNIQUE`)**을 결합(`앱 + DB`)하여 역할을 분담합니다.

| 검증 수단              | 역할 및 목적                                              | 동시성 경합(Race Condition) 방어 |
| ---------------------- | --------------------------------------------------------- | -------------------------------- |
| 애플리케이션 사전 조회 | 사용자에게 명확하고 친절한 비즈니스 오류 코드·메시지 반환 | 방어 불가                        |
| 데이터베이스 고유 제약 | 데이터 무결성의 최종 보증                                 | 완벽히 방어                      |

표에서 `앱 → 도메인`으로 표기된 항목은 애플리케이션 서비스가 필요한 데이터를 조회·조율한 뒤 최종 상태 변경과 불변식 검증은 도메인 엔티티 메서드가 수행함을 뜻합니다.

### 스프링 설정 구성

스프링 설정은 두 클래스로 분리되어 있으며, API 서버(`apps/api`)에서 로드됩니다.

| 설정 클래스               | 등록 대상 컴포넌트                                                                         | 로드 프로세스 |
| ------------------------- | ------------------------------------------------------------------------------------------ | ------------- |
| `MemberCoreConfiguration` | 엔티티, 리포지토리, 유스케이스 서비스, 아웃바운드 어댑터, 세션·표시 이름·reCAPTCHA 정책 빈 | `apps/api`    |
| `MemberWebConfiguration`  | 인증·계정·프로필 REST 컨트롤러 및 무상태 OAuth2 소셜 로그인 핸들러(`MemberSocialLogin`)    | `apps/api`    |

## 데이터 모델

### 애그리거트 구성

회원 컨텍스트는 인증·보안 정보를 관리하는 `Account`와 대외 공개 정보를 관리하는 `Profile` 두 애그리거트, 그리고 애그리거트에 속하지 않는 임시 인증 시도 엔티티들로 구성됩니다.

| 모델 구분             | 루트 및 내부 구성 요소                          | 식별자(PK) 및 설계 특징                                                                                                            |
| --------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `Account` 애그리거트  | `Account` 루트 + `Credential`, `Role` 내부 멤버 | 외부에서는 반드시 `Account` 루트를 통해서만 자격증명을 추가·변경·삭제할 수 있습니다.                                               |
| `Profile` 애그리거트  | `Profile` 루트 (내부 구성 요소 없음)            | `Account`의 ID와 동일한 UUID 값을 공유하여, 타 컨텍스트가 보유한 작성자·소유자 ID로 곧바로 프로필을 조회할 수 있습니다.            |
| 임시 인증 시도 엔티티 | `SignUpAttempt`, `LoginAttempt` 등              | 아직 계정이 생성되기 전이거나 계정 불변식과 무관한 10분 만료 임시 데이터이므로 `Account` 애그리거트 밖의 독립 테이블로 관리합니다. |

`Account`와 `Profile`을 별도 애그리거트로 분리한 이유는 조회 주체와 빈도가 완전히 다르기 때문입니다(프로필은 모든 독자가 매 화면에서 조회하는 반면, 계정은 로그인·보안 설정 시 본인만 조회). 단, 회원가입 시에는 존재하지 않던 두 애그리거트를 최초로 함께 생성(`INSERT`)하므로 경합이 없고, 회원 탈퇴 시에는 계정 종료(`Account.close()`)와 프로필 탈퇴 표시(`Profile.markWithdrawn()`)가 어긋나지 않아야 하므로 예외적으로 한 트랜잭션에서 함께 처리합니다.

### 데이터베이스 스키마

`member` 스키마에서 계정·자격증명·프로필의 유일성을 최종 보증하기 위해 설정한 DB 고유 제약 조건입니다. `profiles`와 `accounts` 사이에는 물리적 외래키(FK)를 두지 않으며, 이메일(비밀번호) 자격증명의 `provider_user_id` 컬럼은 `NULL`이므로 PostgreSQL `UNIQUE` 제약 특성상 여러 회원의 이메일 자격증명이 서로 충돌하지 않습니다.

| 테이블        | 고유 제약 조건                        | 보장하는 도메인 규칙                                  |
| ------------- | ------------------------------------- | ----------------------------------------------------- |
| `accounts`    | `(email)` UNIQUE                      | 동일한 이메일 주소로 하나의 계정만 생성 가능          |
| `credentials` | `(account_id, provider)` UNIQUE       | 한 계정에 동일한 인증 공급자는 하나만 연결 가능       |
| `credentials` | `(provider, provider_user_id)` UNIQUE | 동일한 소셜 신원은 플랫폼 내에서 하나의 계정에만 연결 |
| `profiles`    | `(display_name)` UNIQUE               | 표시 이름(`displayName`)의 전역 유일성 보장           |

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 비즈니스 흐름을 처리하는 도메인 엔티티 메서드와 영역별 세부 구현입니다.

| `01-rules.md`의 규칙                     | 담당 도메인 메서드                                                                   | 구현 방식 및 비고                                                                                          |
| ---------------------------------------- | ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| 흐름 1-3 (이메일 가입 완료)              | `Account.registerWithPassword(email, passwordHash)`                                  | 평문 비밀번호는 응용 서비스에서 해싱(BCrypt 8~72자)해 전달하며 `ACTIVE` 상태로 생성합니다.                 |
| 흐름 2-2 (기존 소셜 자격증명 로그인)     | `account.authenticateWithSocialProvider(provider, providerId)`                       | 재로그인 시에는 `email_verified`를 검사하지 않습니다.                                                      |
| 흐름 2-4, 5 (소셜 신규 가입)             | `Account.registerWithSocialProvider(email, provider, providerId, emailVerified)`     | 미인증 이메일이면 `EmailNotVerifiedByProvider` 예외를 던집니다.                                            |
| 흐름 2-6, 7 (기존 계정에 소셜 자동 연동) | `account.autoLinkSocialProvider(provider, providerId, providerEmail, emailVerified)` | 이메일 검증 여부, 이메일 일치 여부, `ACTIVE` 상태를 검증합니다.                                            |
| 흐름 3 (소셜 수동 연동)                  | `account.linkSocialProvider(provider, providerId)`                                   | 이미 동일 공급자가 연결되어 있으면 거부합니다.                                                             |
| 흐름 4 (비밀번호 설정·변경·재설정)       | `account.authenticateWithPassword(...)`, `account.setPassword(passwordHash)`         | 이메일 자격증명이 없으면 생성하고, 있으면 새 해시로 교체합니다.                                            |
| 흐름 4 (소셜 연동 해제)                  | `account.unlinkProvider(provider)`                                                   | 마지막 남은 유일한 자격증명이면 `LastCredentialRemoval` 예외로 거부합니다.                                 |
| 흐름 4 (이메일 변경 확정)                | `account.changeToVerifiedEmail(verifiedEmail)`                                       | 두 주소의 코드 검증은 응용 서비스가 수행한 뒤 호출합니다.                                                  |
| 흐름 4 (회원 탈퇴)                       | `account.close()`                                                                    | 상태를 `DELETED`로 바꾸고 모든 자격증명을 삭제하며 이메일을 `deleted+<id>@deleted.invalid`로 익명화합니다. |
| 흐름 5 (탈퇴 회원의 프로필 처리)         | `profile.markWithdrawn(at)`                                                          | 표시 이름과 사진은 유지하고 탈퇴 시각만 기록합니다(멱등성 보장).                                           |
| 흐름 6-3 (이메일 코드 로그인 완료)       | `account.authenticateWithEmailCode(confirmedEmail)`                                  | 계정 이메일과 일치하고 `ACTIVE` 상태인지 검증합니다(비밀번호 유무와 무관하게 통과).                        |

### 흐름 1·6. 이메일 코드 시도·세션 관리

이메일 인증 코드 검증과 JWT·리프레시 토큰 세션 관리에 적용된 세부 구현 방식입니다. 로그인 시도는 계정 ID가 아닌 이메일 주소를 저장하고 완료 시점에 계정을 다시 조회하므로, 코드 발송 후 이메일이 변경되거나 탈퇴된 계정에는 이전 코드로 로그인할 수 없습니다.

| 항목                      | 구현 상세                                                                                                                                                                                                    |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 시도 ID 바인딩 및 구조    | 시작 응답으로 받은 `attemptId`를 완료 요청에 함께 제출해야 합니다. 공통 검증 로직은 추상 클래스 `EmailCodeAttempt`에 응집하되 테이블은 `signup_attempts`와 `login_attempts`로 분리해 교차 사용을 차단합니다. |
| 미가입 주소의 코드 로그인 | DB에 시도를 저장하지 않고 가짜 시도 ID만 생성해 동일한 응답을 반환합니다. 해당 ID로 완료를 요청하면 만료된 시도와 동일하게 `VERIFICATION_EXPIRED`를 반환합니다.                                              |
| 실패 횟수 차감 트랜잭션   | 잘못된 코드가 입력되면 실패 횟수 증가를 먼저 DB에 커밋한 뒤 오류를 반환하여, 예외 롤백으로 실패 횟수 차감이 무효화되는 것을 막습니다.                                                                        |
| 코드·토큰 해싱 및 회전    | 인증 코드는 시도 ID를 솔트로 결합한 SHA-256 해시로, 리프레시 토큰은 SHA-256 해시로 저장합니다. 액세스 토큰은 HS256 JWT(15분)이며 리프레시 토큰은 1회 사용 시마다 폐기·재발급(Rotation)합니다.                |
| 초기 표시 이름 및 메일    | 자동 생성된 표시 이름이 충돌하면 최대 5회까지 재생성하며, `contentria.member.mail.mode`가 `smtp`면 실제 메일 발송, `log`면 콘솔에 출력합니다.                                                                |

### 흐름 2·3. 소셜 로그인·수동 연동

무상태(Stateless) API 서버 환경에서 OAuth2/OIDC 인가 흐름과 콜백을 처리하는 구현 방식입니다.

| 항목                    | 구현 상세                                                                                                                                                                                                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 지원 공급자 및 클레임   | Google OIDC를 지원합니다. Spring Security OAuth2 Client로 `state`, `nonce`, PKCE(`S256`) 및 JWKS 서명을 검증하며, UserInfo 호출 없이 ID 토큰의 `sub`, `email`, `email_verified` 클레임만 읽습니다.                                                                     |
| 무상태 인가 요청 쿠키   | HTTP 세션을 쓰지 않으므로 인가 요청 상태를 `HttpOnly` 쿠키(`oauth2AuthRequest`, 유효 시간 10분)에 보관하고 HMAC-SHA256으로 서명합니다.                                                                                                                                 |
| 로그인과 수동 연동 구분 | `?intent=link`로 시작된 요청은 인가 요청 쿠키에 당시 로그인된 계정 ID를 함께 서명해 담고, 콜백 시점에 이를 확인해 수동 연동으로 분기합니다.                                                                                                                            |
| 리다이렉트 및 오류 코드 | 콜백 후 설정된 프론트엔드 경로로만 리다이렉트하며, 실패 시 `?error=<오류코드>`로 전달합니다(`SOCIAL_EMAIL_MISSING`, `SOCIAL_EMAIL_NOT_VERIFIED`, `ACCOUNT_NOT_ACTIVE`, `PROVIDER_ALREADY_LINKED`, `SOCIAL_IDENTITY_TAKEN`, `SIGN_IN_REQUIRED`, `SOCIAL_LOGIN_FAILED`). |

### 흐름 4·5. 계정 보안 설정·프로필 관리

자격증명이나 주요 보안 설정이 변경될 때 수행하는 세션 무효화, 안내 메일 발송 및 데이터 정리 방식입니다.

| 항목                     | 구현 상세                                                                                                                                                                                                                                                                                                                                            |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 세션 무효화 및 안내 메일 | 비밀번호 설정·변경·재설정, 이메일 변경 확정, 회원 탈퇴 시 해당 계정의 리프레시 토큰을 모두 삭제하고 현재 브라우저에만 새 세션을 발급합니다(`SessionService.restartFor`). 보안 안내 메일은 발송 실패가 계정 변경 트랜잭션을 롤백시키지 않도록 커밋 이후(`@TransactionalEventListener(AFTER_COMMIT)`)에 `AccountNoticeListener`가 비동기로 발송합니다. |
| 비밀번호 링크 토큰       | 32바이트 난수의 SHA-256 해시만 `password_tokens` 테이블에 저장합니다(30분, 1회용). 서버 접근 로그나 `Referer` 헤더에 토큰이 남지 않도록 프론트엔드 링크에는 URL 프래그먼트(`#token=...`)로 전달하며, 현재 비밀번호 불일치는 세션 만료(`401`)와 구분되도록 `400 CURRENT_PASSWORD_MISMATCH`를 반환합니다.                                              |
| 이메일 변경의 이중 코드  | 현재 주소용 코드와 새 주소용 코드를 이어 붙인 문자열의 해시를 `email_change_attempts`에 저장하며, 둘 중 하나라도 틀리면 실패 횟수가 차감됩니다.                                                                                                                                                                                                      |
| 활성 재확인 및 탈퇴 정리 | 액세스 토큰 만료 전이라도 정지 계정을 즉시 차단하기 위해 계정 수정 유스케이스는 항상 DB에서 최신 상태를 다시 조회(`loadActive`)합니다. 탈퇴 확정 시에는 남아 있는 모든 시도 기록, 비밀번호 토큰, 리프레시 토큰을 삭제하고 세션 쿠키를 만료시킵니다.                                                                                                  |

### 사전 봇 방지 (`reCAPTCHA`)

외부 Google reCAPTCHA API 연동 시 계층 간 역할 분리와 환경별 처리 방식입니다.

| 항목               | 구현 상세                                                                                                                                                                                                                                                                                                |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 포트와 어댑터 분리 | `adapter/required/captcha/RecaptchaVerifier`는 JDK `HttpClient`(타임아웃 5초)로 Google `siteverify` API를 호출해 점수와 동작명만 반환하고, 기준 점수(`0.5`) 및 동작명(`sign_up`, `login`, `login_code`, `password_reset`) 일치 판단은 응용 계층의 `HumanCheckService`가 수행합니다.                      |
| 오류 코드 및 모드  | v2 추가 인증 요구는 `403 RECAPTCHA_CHALLENGE_REQUIRED`, 검증 실패는 `403 RECAPTCHA_FAILED`, Google API 장애는 `503 RECAPTCHA_UNAVAILABLE`을 반환합니다. `contentria.member.recaptcha.mode`를 운영에서는 `google`로, 로컬 개발에서는 `off`로 설정하며 통합 테스트에서는 `FakeHumanVerifier`를 주입합니다. |

### HTTP 엔드포인트

`application/provided` 패키지에 선언된 인바운드 유스케이스·쿼리 인터페이스와 매핑되는 HTTP 경로 및 내부 공개 쿼리(`member-api`)입니다.

| 도메인 흐름               | 유스케이스 인터페이스                                                 | 구현 서비스                        | HTTP 엔드포인트 및 호출처                                                                           |
| ------------------------- | --------------------------------------------------------------------- | ---------------------------------- | --------------------------------------------------------------------------------------------------- |
| 흐름 1 이메일 가입 시작   | `StartEmailSignUpUseCase`                                             | `EmailSignUpService`               | `POST /auth/sign-up/start` → `202 Accepted` (시도 ID 반환)                                          |
| 흐름 1 이메일 가입 완료   | `CompleteEmailSignUpUseCase`                                          | `EmailSignUpService`               | `POST /auth/sign-up/complete` → `201 Created` (세션 쿠키 발급)                                      |
| 비밀번호 로그인           | `LoginWithPasswordUseCase`                                            | `PasswordLoginService`             | `POST /auth/login` → `200 OK` (세션 쿠키 발급)                                                      |
| 흐름 6 코드 로그인 시작   | `StartEmailCodeLoginUseCase`                                          | `EmailCodeLoginService`            | `POST /auth/login/code/start` → `202 Accepted` (시도 ID 반환)                                       |
| 흐름 6 코드 로그인 완료   | `CompleteEmailCodeLoginUseCase`                                       | `EmailCodeLoginService`            | `POST /auth/login/code/complete` → `200 OK` (세션 쿠키 발급)                                        |
| 흐름 2 소셜 로그인        | `LoginWithSocialProviderUseCase`                                      | `SocialLoginService`               | `GET /oauth2/authorization/google` → `GET /login/oauth2/code/google` → 프론트엔드로 `302`           |
| 흐름 3 소셜 수동 연동     | `LinkSocialProviderUseCase`                                           | `SocialCredentialService`          | `GET /oauth2/authorization/google?intent=link` → 동일 콜백 → 계정 설정 화면으로 `302`               |
| 흐름 4 소셜 연동 해제     | `UnlinkSocialProviderUseCase`                                         | `SocialCredentialService`          | `DELETE /me/credentials/{provider}` → `200 OK` (새 세션 쿠키 발급)                                  |
| 흐름 4 비밀번호 변경      | `ChangePasswordUseCase`                                               | `PasswordService`                  | `POST /me/password` → `200 OK` (새 세션 쿠키 발급)                                                  |
| 흐름 4 비밀번호 최초 설정 | `RequestPasswordSetupLinkUseCase`                                     | `PasswordService`                  | `POST /me/password/setup-link` → `202 Accepted`                                                     |
| 흐름 4 비밀번호 분실      | `RequestPasswordResetLinkUseCase`                                     | `PasswordService`                  | `POST /auth/password/reset-link` → 항상 `202 Accepted`                                              |
| 링크 기반 비밀번호 확정   | `ResetPasswordWithLinkUseCase`                                        | `PasswordService`                  | `POST /auth/password/reset` → `200 OK` (새 세션 쿠키 발급)                                          |
| 흐름 4 이메일 변경        | `StartEmailChangeUseCase`, `CompleteEmailChangeUseCase`               | `EmailChangeService`               | `POST /me/email-change/start` (`202`), `POST /me/email-change/complete` (`200`, 새 세션 쿠키)       |
| 흐름 4 회원 탈퇴          | `StartWithdrawalUseCase`, `CompleteWithdrawalUseCase`                 | `WithdrawalService`                | `POST /me/withdrawal/start` (`202`), `POST /me/withdrawal/complete` (`204`, 세션 쿠키 만료)         |
| 연결된 로그인 수단 조회   | `GetMyCredentialsQuery`                                               | `SocialCredentialService`          | `GET /me/credentials`                                                                               |
| 세션 토큰 갱신·로그아웃   | `RefreshSessionUseCase`, `LogoutUseCase`                              | `SessionService`                   | `POST /auth/refresh`, `POST /auth/logout` (`204 No Content`)                                        |
| 흐름 5 프로필 관리·조회   | `GetMyProfileQuery`, `RenameProfileUseCase`, `GetPublicProfilesQuery` | `ProfileService`                   | `GET /me/profile`, `PATCH /me/profile`, `GET /members/profiles?ids=a,b` (최대 100건, 비로그인 허용) |
| 사전 봇 방지 검증         | `CheckHumanUseCase`                                                   | `HumanCheckService`                | 가입 시작·비밀번호 로그인·코드 로그인 시작·재설정 링크 요청 시 웹 컨트롤러에서 선행 호출            |
| 액세스 토큰 인증 (`-api`) | `AuthenticateAccessTokenQuery`                                        | `AccessTokenAuthenticationService` | `apps/api` 보안 필터가 호출하여 요청자 신원 확인                                                    |
| 계정 상태 조회 (`-api`)   | `GetAccountStatusQuery`                                               | `AccountStatusQueryService`        | 출판·상호작용 컨텍스트의 번역 계층이 호출 (`ACTIVE`, `SUSPENDED`, `CLOSED` 반환)                    |
