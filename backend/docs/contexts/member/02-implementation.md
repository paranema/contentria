# 회원 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 각 규칙을 어느 계층과 컴포넌트에 배치해 구현했는지 기록합니다.
제품의 비즈니스 규칙이 변경되면 `01-rules.md`를, 계층 배치나 기술적 구현 방식이 변경되면 이 문서를 수정합니다.

- 내부 계층 구조는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **헥사고날 아키텍처** 규칙을 따릅니다.

## 도메인 불변식과 애플리케이션 정책의 구분 기준

> **단일 애그리거트 내부 상태만으로 판단할 수 있으면 도메인 불변식(`도메인`)이고, 다른 애그리거트나 외부 저장소를 조회해야 판단할 수 있으면 애플리케이션 정책(`앱`)입니다.**

예를 들어 `Account` 엔티티 객체 하나만으로는 "이 이메일을 사용하는 다른 계정이 이미 존재하는가?"를 알 수 없습니다. 이를 도메인 엔티티가 직접 판단하게 하려면 리포지토리를 도메인 내부로 끌어들여야 하므로 계층 의존 방향이 무너집니다.

따라서 중복 검사와 같은 규칙은 **애플리케이션 계층의 사전 조회**와 **데이터베이스 고유 제약(UNIQUE)**을 결합(`앱 + DB`)하여 구현하며, 두 수단은 아래와 같이 역할을 분담합니다.

| 검증 수단            | 역할 및 목적                                             | 동시성 경합(Race Condition) 방어 |
| -------------------- | -------------------------------------------------------- | -------------------------------- |
| 애플리케이션 사전 조회| 사용자에게 명확하고 친절한 비즈니스 오류 코드·메시지 반환 | 방어 불가                        |
| 데이터베이스 제약    | 데이터 무결성의 최종 보증                                | 완벽히 방어                      |

두 수단 모두 필수적입니다. 사전 조회만 두면 동시 요청 경합 시 중복 데이터가 생성되고, DB 제약만 두면 일상적인 중복 입력에도 저수준 DB 예외가 발생합니다.
한편 표에서 `앱 → 도메인`으로 표기된 항목은 애플리케이션 서비스가 필요한 데이터를 조회·조율한 뒤 최종 상태 변경과 불변식 검증은 도메인 엔티티 메서드가 수행함을 뜻합니다.

## 애그리거트 경계 및 도메인 모델

회원 컨텍스트는 인증·보안 정보를 관리하는 `Account`와 대외 공개 정보를 관리하는 `Profile` 두 애그리거트로 구성됩니다.

- **`Account` 애그리거트**: `Account`가 애그리거트 루트이며, `Credential`과 `Role`을 내부 멤버로 소유합니다. 외부에서는 반드시 `Account` 루트를 통해서만 자격증명을 추가·변경·삭제할 수 있습니다.
- **`Profile` 애그리거트**: `Account`와 분리된 독립 애그리거트이며, 식별자(PK)는 `Account`의 ID와 동일한 UUID 값을 공유합니다. 덕분에 다른 컨텍스트는 이미 보유하고 있는 작성자·소유자 ID로 곧바로 프로필을 조회할 수 있습니다.

### `Account`와 `Profile`을 별도 애그리거트로 분리하되 가입·탈퇴 시 한 트랜잭션으로 묶는 이유

- **분리 이유**: 조회 주체와 빈도가 완전히 다릅니다. 프로필은 게시글과 댓글 화면을 렌더링할 때마다 모든 독자가 조회하는 반면, 계정은 로그인이나 보안 설정 시 본인만 조회합니다. 하나의 애그리거트로 묶으면 공개 프로필을 읽을 때마다 이메일과 권한 등 민감 정보가 함께 조회됩니다.
- **가입·탈퇴 트랜잭션 예외 허용**: "한 트랜잭션에는 하나의 애그리거트만 수정한다"는 원칙은 여러 애그리거트를 동시에 수정할 때 발생하는 잠금 경합을 피하기 위한 것입니다.
  - 회원가입 시에는 존재하지 않던 두 애그리거트를 최초로 함께 생성(`INSERT`)하므로 다른 트랜잭션과의 경합이 전혀 발생하지 않습니다.
  - 회원 탈퇴 시에도 계정을 닫는 작업(`Account.close()`)과 프로필에 탈퇴 표시를 남기는 작업(`Profile.markWithdrawn()`)을 한 트랜잭션으로 처리하여, 탈퇴한 회원의 프로필이 활성 회원처럼 보이는 불일치를 방지합니다.

### 애그리거트에 속하지 않는 임시 시도 모델

- **가입 시도(`SignUpAttempt`)와 로그인 시도(`LoginAttempt`)**는 `Account` 애그리거트의 일부가 아닙니다. 아직 계정이 생성되기 전이거나 계정 불변식과 무관한 10분 만료 임시 데이터이므로 별도의 엔티티·테이블(`signup_attempts`, `login_attempts`)로 관리합니다.
- 특히 로그인 시도는 계정 ID가 아닌 이메일 주소를 저장하고, 코드 검증 완료 시점에 해당 이메일로 계정을 다시 조회합니다. 따라서 코드를 발송한 뒤 이메일이 변경되거나 탈퇴 처리된 계정에는 이전 주소로 발급된 코드로 로그인할 수 없습니다.

## 데이터베이스 스키마 및 제약 조건

`member` 스키마에서 계정·자격증명·프로필의 유일성을 최종 보증하기 위해 설정한 DB 고유 제약 조건입니다.

| 제약 조건                                           | 보장하는 도메인 규칙                                |
| --------------------------------------------------- | --------------------------------------------------- |
| `accounts(email)` UNIQUE                            | 동일한 이메일 주소로 하나의 계정만 생성 가능        |
| `credentials(account_id, provider)` UNIQUE          | 한 계정에 동일한 인증 공급자는 하나만 연결 가능     |
| `credentials(provider, provider_user_id)` UNIQUE    | 동일한 소셜 신원은 플랫폼 내에서 하나의 계정에만 연결|
| `profiles(display_name)` UNIQUE                     | 표시 이름(`displayName`)의 전역 유일성 보장         |

- `profiles`와 `accounts` 사이에는 물리적 외래키(FK)를 두지 않습니다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 `외래키` 절 참고). 두 행의 생성 정합성은 가입 유스케이스가 보장합니다.
- 이메일(비밀번호) 자격증명의 `provider_user_id` 컬럼은 `NULL`입니다. PostgreSQL의 `UNIQUE` 제약은 `NULL` 값을 서로 다른 값으로 취급하므로 여러 회원의 이메일 자격증명이 충돌하지 않습니다.

## 계정 보안 변경에 따른 세션 무효화 및 안내 메일 처리

계정의 자격증명이나 주요 보안 설정이 변경되었을 때 수행하는 세션 무효화 및 보안 안내 메일 발송 처리입니다.

| 발생 사건                        | 후속 처리 내용                                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------------------- |
| 소셜 계정 연동 (자동 연동 포함)  | 계정 이메일로 연동 완료 안내 메일 발송                                                             |
| 소셜 계정 연동 해제              | 계정 이메일로 연동 해제 안내 메일 발송                                                             |
| 비밀번호 최초 설정·변경·재설정   | 안내 메일 발송 및 현재 브라우저를 제외한 다른 기기의 세션 전체 무효화                              |
| 이메일 변경 요청                 | 현재 이메일과 새 이메일 양쪽으로 각각 인증 코드 발송                                               |
| 이메일 변경 확정                 | 기존 이메일로 변경 완료 안내 메일 발송, 다른 세션 전체 무효화, 기존 이메일로 발급된 비밀번호 토큰 폐기|
| 회원 탈퇴 확정                   | 해당 계정의 모든 세션 무효화                                                                       |

- **세션 무효화 방식**: 해당 계정의 리프레시 토큰을 모두 삭제하고, 요청을 보낸 현재 브라우저에만 새 세션을 발급합니다(`SessionService.restartFor`). 다른 기기가 보유한 액세스 토큰은 만료 시각(최대 15분)까지만 유효하며 이후 갱신이 거부됩니다.
- **안내 메일 비동기 발송**: 안내 메일 발송 실패가 이미 완료된 계정 변경 트랜잭션을 롤백시키지 않도록, 트랜잭션 커밋 이후(`@TransactionalEventListener(AFTER_COMMIT)`)에 `AccountNoticeListener`가 `AccountNoticeSender`를 호출해 발송합니다.

## 도메인 규칙과 엔티티 메서드 매핑

[01-rules.md](01-rules.md)에서 검증 계층이 `도메인` 또는 `앱 → 도메인`인 비즈니스 규칙을 담당하는 `Account`·`Profile` 엔티티 메서드 목록입니다.

| `01-rules.md`의 규칙                      | 담당 도메인 메서드                                                                  | 구현 방식 및 비고                                                                                 |
| ----------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 흐름 1-3 (코드 검증 후 이메일 가입 완료)  | `Account.registerWithPassword(email, passwordHash)`                                 | 평문 비밀번호는 응용 서비스에서 해싱하여 전달하며, 계정은 `ACTIVE` 상태로 생성됩니다.             |
| 흐름 2-2 (기존 소셜 자격증명으로 로그인)  | `account.authenticateWithSocialProvider(provider, providerId)`                      | 재로그인 시에는 `email_verified`를 검사하지 않습니다.                                             |
| 흐름 2-4, 5 (소셜 신규 가입)              | `Account.registerWithSocialProvider(email, provider, providerId, emailVerified)`    | 미인증 이메일이면 `EmailNotVerifiedByProvider` 예외를 던집니다.                                   |
| 흐름 2-6, 7 (기존 계정에 소셜 자동 연동)  | `account.autoLinkSocialProvider(provider, providerId, providerEmail, emailVerified)`| 이메일 검증 여부, 이메일 일치 여부, `ACTIVE` 상태를 검증합니다.                                   |
| 흐름 3 (로그인 상태에서 소셜 수동 연동)   | `account.linkSocialProvider(provider, providerId)`                                  | 이미 동일 공급자가 연결되어 있으면 거부합니다.                                                    |
| 흐름 4 비밀번호 최초 설정 및 분실 재설정  | `account.setPassword(passwordHash)`                                                 | 이메일 자격증명이 없으면 생성하고, 있으면 새 해시로 교체합니다.                                   |
| 흐름 4 비밀번호 변경                      | `account.authenticateWithPassword(...)` 후 `setPassword(...)`                       | 현재 비밀번호 일치 여부를 먼저 검증한 뒤 교체합니다.                                              |
| 흐름 4 소셜 연동 해제                     | `account.unlinkProvider(provider)`                                                  | 마지막 남은 유일한 자격증명이면 `LastCredentialRemoval` 예외로 거부합니다.                        |
| 흐름 4 이메일 변경 확정                   | `account.changeToVerifiedEmail(verifiedEmail)`                                      | 두 주소의 코드 검증은 응용 서비스가 수행한 뒤 호출합니다.                                         |
| 흐름 4 회원 탈퇴 (`ACTIVE`)               | `account.close()`                                                                   | 상태를 `DELETED`로 바꾸고 모든 자격증명을 삭제하며, 이메일을 `deleted+<id>@deleted.invalid`로 익명화합니다.|
| 흐름 5 탈퇴 회원의 프로필 처리            | `profile.markWithdrawn(at)`                                                         | 표시 이름과 사진은 유지하고 탈퇴 시각만 기록합니다(멱등성 보장).                                  |
| 흐름 6-3 (이메일 코드 로그인 완료)        | `account.authenticateWithEmailCode(confirmedEmail)`                                 | 계정 이메일과 일치하고 `ACTIVE` 상태인지 검증합니다(비밀번호 유무와 무관하게 통과).               |

## 유스케이스 및 HTTP 엔드포인트

`application/provided` 패키지에 선언된 인바운드 유스케이스 인터페이스와 구현 서비스, 그리고 매핑되는 HTTP 경로입니다.

| 도메인 흐름               | 유스케이스 인터페이스                                   | 구현 서비스                       | HTTP 엔드포인트 및 응답                                                                                  |
| ------------------------- | ------------------------------------------------------- | --------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 흐름 1 이메일 가입 시작   | `StartEmailSignUpUseCase`                               | `EmailSignUpService`              | `POST /auth/sign-up/start` → `202 Accepted` (시도 ID 반환)                                               |
| 흐름 1 이메일 가입 완료   | `CompleteEmailSignUpUseCase`                            | `EmailSignUpService`              | `POST /auth/sign-up/complete` → `201 Created` (세션 쿠키 발급)                                           |
| 비밀번호 로그인           | `LoginWithPasswordUseCase`                              | `PasswordLoginService`            | `POST /auth/login` → `200 OK` (세션 쿠키 발급)                                                           |
| 흐름 6 코드 로그인 시작   | `StartEmailCodeLoginUseCase`                            | `EmailCodeLoginService`           | `POST /auth/login/code/start` → `202 Accepted` (시도 ID 반환)                                            |
| 흐름 6 코드 로그인 완료   | `CompleteEmailCodeLoginUseCase`                         | `EmailCodeLoginService`           | `POST /auth/login/code/complete` → `200 OK` (세션 쿠키 발급)                                             |
| 흐름 2 소셜 로그인        | `LoginWithSocialProviderUseCase`                        | `SocialLoginService`              | `GET /oauth2/authorization/google` → Google 인증 → `GET /login/oauth2/code/google` → 프론트엔드로 `302`  |
| 흐름 3 소셜 수동 연동     | `LinkSocialProviderUseCase`                             | `SocialCredentialService`         | `GET /oauth2/authorization/google?intent=link` → 동일 콜백 → 계정 설정 화면으로 `302`                     |
| 흐름 4 소셜 연동 해제     | `UnlinkSocialProviderUseCase`                           | `SocialCredentialService`         | `DELETE /me/credentials/{provider}` → `200 OK` (새 세션 쿠키 발급)                                       |
| 흐름 4 비밀번호 변경      | `ChangePasswordUseCase`                                 | `PasswordService`                 | `POST /me/password` → `200 OK` (새 세션 쿠키 발급)                                                       |
| 흐름 4 비밀번호 최초 설정 | `RequestPasswordSetupLinkUseCase`                       | `PasswordService`                 | `POST /me/password/setup-link` → `202 Accepted` (설정 링크 메일 발송)                                    |
| 흐름 4 비밀번호 분실      | `RequestPasswordResetLinkUseCase`                       | `PasswordService`                 | `POST /auth/password/reset-link` → 항상 `202 Accepted`                                                   |
| 링크 기반 비밀번호 확정   | `ResetPasswordWithLinkUseCase`                          | `PasswordService`                 | `POST /auth/password/reset` → `200 OK` (새 세션 쿠키 발급)                                               |
| 흐름 4 이메일 변경        | `StartEmailChangeUseCase`, `CompleteEmailChangeUseCase` | `EmailChangeService`              | `POST /me/email-change/start` (`202`), `POST /me/email-change/complete` (`200`, 새 세션 쿠키)             |
| 흐름 4 회원 탈퇴          | `StartWithdrawalUseCase`, `CompleteWithdrawalUseCase`   | `WithdrawalService`               | `POST /me/withdrawal/start` (`202`), `POST /me/withdrawal/complete` (`204`, 세션 쿠키 만료)               |
| 연결된 로그인 수단 조회   | `GetMyCredentialsQuery`                                 | `SocialCredentialService`         | `GET /me/credentials`                                                                                    |
| 세션 토큰 갱신            | `RefreshSessionUseCase`                                 | `SessionService`                  | `POST /auth/refresh` (리프레시 토큰 쿠키 사용)                                                           |
| 로그아웃                  | `LogoutUseCase`                                         | `SessionService`                  | `POST /auth/logout` → 항상 `204 No Content` (해당 리프레시 토큰 삭제 및 쿠키 만료)                       |
| 흐름 5 내 프로필 관리     | `GetMyProfileQuery`, `RenameProfileUseCase`             | `ProfileService`                  | `GET /me/profile`, `PATCH /me/profile`                                                                   |
| 흐름 5 공개 프로필 조회   | `GetPublicProfilesQuery`                                | `ProfileService`                  | `GET /members/profiles?ids=a,b` (최대 100건, 비로그인 허용, 표시 이름·사진·탈퇴 여부 반환)               |
| 봇 방지 (사람 확인)       | `CheckHumanUseCase`                                     | `HumanCheckService`               | 가입 시작·비밀번호 로그인·코드 로그인 시작·재설정 링크 요청 시 웹 컨트롤러에서 선행 호출                  |
| 액세스 토큰 인증 (`-api`) | `AuthenticateAccessTokenQuery`                          | `AccessTokenAuthenticationService`| `apps/api`의 보안 필터가 호출하여 요청자 신원 확인                                                       |
| 계정 상태 조회 (`-api`)   | `GetAccountStatusQuery`                                 | `AccountStatusQueryService`       | 출판·상호작용 컨텍스트의 번역 계층이 호출 (`ACTIVE`, `SUSPENDED`, `CLOSED` 반환)                         |

## 영역별 세부 구현 결정

인증·세션·외부 연동 과정에서 보안성과 정합성을 확보하기 위해 내린 영역별 세부 구현 사항입니다.

### 1. 인증 코드 시도 및 세션 관리

이메일 인증 코드 검증과 JWT·리프레시 토큰 세션 관리에 적용된 세부 구현 방식입니다.

| 항목                    | 구현 상세                                                                                                                                      |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 시도 ID와 세션 바인딩   | 시작 응답으로 받은 시도 ID(`attemptId`)를 완료 요청에 함께 제출해야만 코드를 검증할 수 있습니다.                                               |
| 시도 엔티티 구조        | 만료 시간, 실패 횟수 차감, 해시 검증 로직은 추상 기반 클래스인 `EmailCodeAttempt`에 응집되어 있으며, 테이블은 `signup_attempts`와 `login_attempts`로 분리하여 교차 사용을 차단합니다. |
| 미가입 주소의 코드 로그인| DB에 시도를 저장하지 않고 가짜 시도 ID만 생성해 동일한 응답 형태로 반환합니다. 해당 ID로 완료를 요청하면 만료된 시도와 동일하게 `VERIFICATION_EXPIRED`를 반환합니다. |
| 실패 횟수 차감 트랜잭션 | 잘못된 코드가 입력되면 실패 횟수 증가를 먼저 DB에 커밋한 뒤 오류를 반환합니다(예외로 롤백되면 실패 횟수 증가까지 함께 롤백되기 때문).          |
| 코드 및 토큰 해싱       | 평문은 저장하지 않습니다. 인증 코드는 시도 ID를 솔트처럼 결합한 SHA-256 해시로, 리프레시 토큰은 SHA-256 해시로 저장합니다.                     |
| 액세스 토큰과 리프레시 토큰| 액세스 토큰은 HS256 JWT(유효 시간 15분)를 사용하며, 리프레시 토큰은 1회 사용 시마다 폐기하고 새로 발급(Rotation)합니다.                        |
| 초기 표시 이름 충돌 재시도| 자동 생성된 표시 이름이 이미 존재하면 최대 5회까지 재생성합니다.                                                                               |
| 비밀번호 길이 제한      | BCrypt 알고리즘의 72바이트 입력 제한에 맞춰 8자 이상 72자 이하로 검증합니다.                                                                   |
| 로컬 개발용 메일 모드   | `contentria.member.mail.mode` 설정이 `smtp`이면 실제 메일을 발송하고, `log`이면 콘솔 로그로 인증 코드를 출력합니다.                            |

### 2. 소셜 로그인 (OAuth2 / OIDC)

무상태(Stateless) API 서버 환경에서 OAuth2/OIDC 인가 흐름과 콜백을 안전하게 처리하기 위한 구현 방식입니다.

| 항목                    | 구현 상세                                                                                                                                      |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 지원 공급자 및 프로토콜 | 현재 Google OIDC를 지원합니다. Spring Security OAuth2 Client를 통해 `state`, OIDC `nonce`, PKCE(`S256`) 및 ID 토큰 JWKS 서명을 검증합니다.     |
| 클레임 추출             | 별도의 UserInfo 엔드포인트를 호출하지 않고(`setRetrieveUserInfo { false }`), ID 토큰의 `sub`, `email`, `email_verified` 클레임만 읽습니다.     |
| 무상태 인가 요청 쿠키   | API 서버는 HTTP 세션을 사용하지 않으므로 진행 중인 OAuth2 인가 요청 상태를 `HttpOnly` 쿠키(`oauth2AuthRequest`, 유효 시간 10분)에 보관하며, 위변조 방지를 위해 HMAC-SHA256으로 서명합니다. |
| 로그인과 수동 연동 구분 | `?intent=link`로 시작된 요청은 인가 요청 쿠키에 당시 로그인된 계정 ID를 함께 서명해 담습니다. 콜백 시점에 이를 확인하여 수동 연동으로 분기합니다.|
| 리다이렉트 및 오류 코드 | 콜백 완료 후에는 설정(`contentria.member.social.*`)에 지정된 프론트엔드 경로로만 리다이렉트하며, 실패 시 `?error=<오류코드>` 쿼리 파라미터로 전달합니다 (`SOCIAL_EMAIL_MISSING`, `SOCIAL_EMAIL_NOT_VERIFIED`, `ACCOUNT_NOT_ACTIVE`, `PROVIDER_ALREADY_LINKED`, `SOCIAL_IDENTITY_TAKEN`, `SIGN_IN_REQUIRED`, `SOCIAL_LOGIN_FAILED`). |

### 3. 계정 관리 및 보안 설정

비밀번호 재설정 링크, 이메일 변경, 회원 탈퇴 처리 시 적용되는 보안 검증 및 데이터 정리 방식입니다.

| 항목                    | 구현 상세                                                                                                                                      |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 비밀번호 링크 토큰      | 32바이트 난수를 생성해 SHA-256 해시만 `password_tokens` 테이블에 저장합니다(유효 시간 30분, 1회용). 프론트엔드 링크에는 서버 접근 로그나 `Referer` 헤더에 토큰이 남지 않도록 URL 프래그먼트(`#token=...`)로 전달합니다. |
| 비밀번호 오류 코드 구분 | 현재 비밀번호 불일치는 세션 만료(`401`)와 혼동되지 않도록 `400 CURRENT_PASSWORD_MISMATCH`로 반환합니다.                                         |
| 이메일 변경의 이중 코드 | 현재 주소용 코드와 새 주소용 코드를 이어 붙인 문자열의 해시를 `email_change_attempts`에 저장하며, 둘 중 하나라도 틀리면 실패 횟수가 차감됩니다. |
| 상태 변경 전 활성 재확인| 액세스 토큰은 계정 정지 직후에도 만료 전까지 유효하므로, 계정 정보를 수정하는 모든 유스케이스는 DB에서 최신 계정 상태를 다시 조회(`loadActive`)하여 정지 계정이면 `403 ACCOUNT_NOT_ACTIVE`로 차단합니다. |
| 탈퇴 시 연관 데이터 정리| 계정 탈퇴 확정 시 남아 있는 탈퇴 시도, 이메일 변경 시도, 로그인 시도, 비밀번호 토큰, 리프레시 토큰을 모두 삭제하고 세션 쿠키를 만료시킵니다.   |

### 4. 봇 방지 (`reCAPTCHA`)

외부 Google reCAPTCHA API 연동 시 계층 간 역할 분리와 장애·환경별 처리 방식입니다.

| 항목                    | 구현 상세                                                                                                                                      |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 포트와 어댑터 분리      | `adapter/required/captcha/RecaptchaVerifier`는 JDK `HttpClient`(타임아웃 5초)로 Google `siteverify` API를 호출해 점수와 동작명만 반환하고, 기준 점수(`0.5`) 및 동작명(`sign_up`, `login`, `login_code`, `password_reset`) 일치 판단은 응용 계층의 `HumanCheckService`가 수행합니다. |
| 오류 응답 코드          | v2 체크박스 추가 인증 요구는 `403 RECAPTCHA_CHALLENGE_REQUIRED`, 검증 실패는 `403 RECAPTCHA_FAILED`, Google API 장애는 `503 RECAPTCHA_UNAVAILABLE`을 반환합니다. |
| 환경별 모드 설정        | `contentria.member.recaptcha.mode`를 운영에서는 `google`로, 로컬 개발에서는 `off`로 설정할 수 있으며, 통합 테스트에서는 `FakeHumanVerifier`를 주입해 검증합니다. |
