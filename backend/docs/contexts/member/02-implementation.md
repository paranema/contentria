# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.
규칙이 바뀌면 01을, 구현 방법이 바뀌면 이 문서를 고친다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 헥사고날 규칙을 따른다.

## 불변식과 정책을 가르는 기준

> 애그리거트 **하나만** 보고 판단할 수 있으면 불변식(도메인), 다른 애그리거트를 조회해야 하면 정책(앱)이다.

`Account` 객체는 다른 `Account`를 볼 수 없다. "이 이메일을 쓰는 계정이 이미 있는가"는
애그리거트 밖의 사실이므로 도메인 메서드가 답할 수 없다. 답하게 하려면 리포지토리를
도메인에 끌어들여야 하고, 그러면 의존 방향이 뒤집힌다.

이런 규칙은 애플리케이션이 조회해서 판단하고, DB 제약이 실제 보증을 맡는다. 01에서 `앱 + DB`로 적힌 줄이다.

| | 애플리케이션 조회 | DB 제약 |
|---|---|---|
| 목적 | 사용자에게 쓸모 있는 오류 메시지 | 실제 보증 |
| 동시 요청 두 개 | 못 잡는다 | 잡는다 |

둘 다 필요하다. 조회만 두면 동시 요청에 뚫리고, 제약만 두면 사용자가 제약 위반 예외를 본다.

`앱 → 도메인`은 애플리케이션이 조회와 오케스트레이션을 하고 최종 판단은 도메인이 한다는 뜻이다.

## 애그리거트 경계

`Account`가 루트, `Credential`과 `Role`이 멤버다. `Credential`은 `Account`를 통해서만 도달한다.

`Profile`은 별도 애그리거트다. 식별자는 계정 ID와 같은 값을 쓴다.
다른 컨텍스트는 이미 계정 ID를 작성자나 소유자 참조로 들고 있으므로, 그 ID로 프로필을 바로 읽는다.

`Account`와 한 애그리거트로 합치지 않는 이유는 읽는 쪽이 다르기 때문이다.
프로필은 글과 댓글을 보여줄 때마다 누구나 읽고, 계정은 인증할 때 본인만 읽는다.
한 애그리거트로 두면 프로필을 읽을 때 이메일과 권한이 함께 로딩된다.
두 애그리거트는 가입 유스케이스에서 한 트랜잭션으로 함께 만든다. "한 트랜잭션에는 한 애그리거트"의 예외다.
그 규칙은 여러 애그리거트를 동시에 **수정**할 때의 경합을 피하려는 것인데, 새로 만드는 두 애그리거트에는 경합할 다른 트랜잭션이 없다. 생성 뒤의 변경은 각각 따로 한다.

탈퇴가 두 번째 예외다. 계정을 닫고 프로필에 탈퇴 표시를 한 트랜잭션에서 한다. 닫힌 계정의 프로필이 탈퇴 표시 없이 남으면 떠난 회원이 있는 것처럼 보인다. 두 애그리거트를 동시에 고치는 다른 흐름은 없어 경합할 상대가 프로필 이름 변경뿐이다.

**가입 시도는 계정 애그리거트가 아니다.** 아직 계정이 아니고 계정 불변식과 무관하다.
만료가 있는 임시 데이터이므로 캐시여도 되고 테이블이어도 된다.

**로그인 시도도 계정 애그리거트가 아니다.** 계정 ID가 아니라 주소를 담고, 코드가 돌아오면 주소로 계정을 다시 찾는다.
코드를 보낸 뒤 주소가 바뀌거나 탈퇴로 익명화된 계정에는 옛 주소로 받은 코드로 들어올 수 없다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `accounts.email` UNIQUE | 한 이메일에 계정 하나 |
| `credentials(account_id, provider)` UNIQUE | 한 계정에 같은 공급자 하나 |
| `credentials(provider, provider_user_id)` UNIQUE | 한 소셜 신원은 계정 하나에만 |
| `profiles.display_name` UNIQUE | 표시 이름 중복 금지 |

"프로필은 계정이 있어야 존재한다"를 보증하는 DB 제약은 두지 않는다. 두 애그리거트 사이이기 때문이다([architecture/03-context-integration.md](../../architecture/03-context-integration.md)의 "외래키"). 가입 유스케이스가 두 행을 함께 만들어 보증한다.

로컬 자격증명의 `provider_user_id`는 NULL이다. PostgreSQL은 UNIQUE 비교에서 NULL을
서로 다른 값으로 취급하므로 충돌하지 않는다.

여기에 계정 id를 넣으면 제약이 하나도 늘지 않는다. `(account_id, provider)` UNIQUE가
이미 같은 것을 보증한다. 대신 컬럼의 의미가 행마다 달라진다.

## 이벤트

| 이벤트 | 후속 |
|---|---|
| 소셜 연동됨 (자동 연동 포함) | 계정 이메일로 안내 메일 |
| 소셜 연동 해제됨 | 계정 이메일로 안내 메일 |
| 비밀번호 설정·변경·재설정됨 | 안내 메일, 다른 세션 무효화 |
| 이메일 변경 요청됨 | 현재 주소로 확인 코드(알림 겸), 새 주소로 확인 코드 |
| 이메일 변경 확정됨 | 기존 주소로 안내 메일, 다른 세션 무효화, 기존 주소로 보낸 비밀번호 링크 무효화 |
| 계정 탈퇴됨 | 모든 세션 무효화 |

세션 무효화는 그 계정의 리프레시 토큰을 모두 지우고, 요청한 브라우저에만 새 세션을 주는 것이다(`SessionService.restartFor`). 다른 기기의 액세스 토큰은 만료(최대 15분)까지 통과한다.
확인 코드와 비밀번호 링크는 이벤트가 아니라 유스케이스 안에서 바로 보낸다. 받지 못하면 사용자가 다시 요청한다.

메일 발송이 실패해도 트랜잭션을 되돌리지 않는다. 커밋 후 처리한다.

지금은 같은 프로세스 안에서 처리한다. 서비스가 `AccountNoticeRequested`를 발행하고, `AccountNoticeListener`가 커밋 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) `AccountNoticeSender`로 보낸다.
커밋과 발송 사이에 프로세스가 죽으면 그 안내는 빠진다. 아웃박스를 만들 때까지 받아들인다. 발송 실패는 로그로 남기고 삼킨다. 알리려는 변경은 이미 일어났기 때문이다.

## 규칙과 도메인 메서드

01에서 계층이 `도메인`이거나 `앱 → 도메인`인 규칙을 어느 메서드가 맡는지 적는다. 유스케이스는 이 메서드를 호출한다.

| 01의 규칙 | `Account` 메서드 | 비고 |
|---|---|---|
| 흐름 1의 3번 (코드 확인 후 계정 생성) | `registerWithPassword(email, passwordHash)` | 해시를 받는다. 원시 비밀번호는 유스케이스에만 있다. 계정은 `ACTIVE`로 시작한다 |
| 흐름 2의 2번 (기존 자격증명으로 로그인) | `authenticateWithSocialProvider(provider, providerId)` | `email_verified`를 보지 않는다 |
| 흐름 2의 4, 5번 (소셜 가입) | `registerWithSocialProvider(email, provider, providerId, emailVerifiedByProvider)` | 확인되지 않은 주소면 `EmailNotVerifiedByProvider` |
| 흐름 2의 6, 7번 (자동 연동) | `autoLinkSocialProvider(provider, providerId, providerEmail, emailVerifiedByProvider)` | 확인된 주소, 같은 주소, 활성 계정일 때만. 같은 주소의 계정을 찾는 것은 유스케이스다 |
| 흐름 3 (수동 연동) | `linkSocialProvider(provider, providerId)` | `email_verified`를 보지 않는다 |
| 흐름 4 비밀번호 최초 설정, 재설정 | `setPassword(passwordHash)` | 이메일 자격증명이 없으면 만들고 있으면 교체 |
| 흐름 4 비밀번호 변경 | `authenticateWithPassword` 후 `setPassword` | 현재 비밀번호 확인을 유스케이스가 두 메서드로 조합한다 |
| 흐름 4 연동 해제 | `unlinkProvider(provider)` | 마지막 자격증명이면 `LastCredentialRemoval` |
| 흐름 4 이메일 변경 확정 | `changeToVerifiedEmail(verifiedEmail)` | 확인 절차는 유스케이스. 메서드 이름이 계약을 드러낸다 |
| 흐름 4 탈퇴 (ACTIVE) | `close()` | `DELETED`로 바꾸고, 자격증명을 모두 지우고, 이메일을 `deleted+<id>@deleted.invalid`로 익명화한다 |
| 흐름 6의 3번 (코드 로그인) | `authenticateWithEmailCode(confirmedEmail)` | 코드 확인은 유스케이스. 계정 주소와 같은 주소인지, ACTIVE인지만 본다. 비밀번호가 없는 계정도 통과한다 |
| 흐름 5 탈퇴한 회원의 프로필 | `Profile.markWithdrawn(at)` | 이름과 사진은 남기고 탈퇴 시각만 붙인다. 두 번 불러도 처음 시각이다 |
| 공통 이메일 정규화 | 가입, 이메일 변경, 자동 연동이 같은 정규화를 쓴다 | 앞뒤 공백 제거와 소문자화. 점과 `+` 이후는 지우지 않는다 |

## 유스케이스

`application/provided`의 유스케이스와 그 구현이다. 하위 단계마다 늘어난다.

| 01의 흐름 | 유스케이스 | 구현 | HTTP |
|---|---|---|---|
| 흐름 1의 1, 2번 | `StartEmailSignUpUseCase` | `EmailSignUpService` | `POST /auth/sign-up/start` → 202, 시도 ID |
| 흐름 1의 3번 | `CompleteEmailSignUpUseCase` | `EmailSignUpService` | `POST /auth/sign-up/complete` → 201, 세션 쿠키 |
| 비밀번호 로그인 | `LoginWithPasswordUseCase` | `PasswordLoginService` | `POST /auth/login` |
| 흐름 6의 1, 2번 | `StartEmailCodeLoginUseCase` | `EmailCodeLoginService` | `POST /auth/login/code/start` → 202, 시도 ID |
| 흐름 6의 3번 | `CompleteEmailCodeLoginUseCase` | `EmailCodeLoginService` | `POST /auth/login/code/complete` → 200, 세션 쿠키 |
| 흐름 2 소셜 로그인 | `LoginWithSocialProviderUseCase` | `SocialLoginService` | `GET /oauth2/authorization/google` → 구글 → `GET /login/oauth2/code/google` → 프론트엔드로 302, 세션 쿠키 |
| 흐름 3 수동 연동 | `LinkSocialProviderUseCase` | `SocialCredentialService` | `GET /oauth2/authorization/google?intent=link` → 구글 → 같은 콜백 → 설정 화면으로 302 |
| 흐름 4 연동 해제 | `UnlinkSocialProviderUseCase` | `SocialCredentialService` | `DELETE /me/credentials/{provider}` → 200, 새 세션 쿠키 |
| 흐름 4 비밀번호 변경 | `ChangePasswordUseCase` | `PasswordService` | `POST /me/password` → 200, 새 세션 쿠키 |
| 흐름 4 비밀번호 최초 설정 | `RequestPasswordSetupLinkUseCase` | `PasswordService` | `POST /me/password/setup-link` → 202, 링크 메일 |
| 흐름 4 비밀번호 분실 | `RequestPasswordResetLinkUseCase` | `PasswordService` | `POST /auth/password/reset-link` → 항상 202 |
| 링크로 설정·재설정 | `ResetPasswordWithLinkUseCase` | `PasswordService` | `POST /auth/password/reset` → 200, 새 세션 쿠키 |
| 흐름 4 이메일 변경 | `StartEmailChangeUseCase`, `CompleteEmailChangeUseCase` | `EmailChangeService` | `POST /me/email-change/start` → 202, `/complete` → 200, 새 세션 쿠키 |
| 흐름 4 탈퇴 | `StartWithdrawalUseCase`, `CompleteWithdrawalUseCase` | `WithdrawalService` | `POST /me/withdrawal/start` → 202, `/complete` → 204, 쿠키 만료 |
| 설정 화면의 로그인 수단 | `GetMyCredentialsQuery` | `SocialCredentialService` | `GET /me/credentials` |
| 세션 갱신 | `RefreshSessionUseCase` | `SessionService` | `POST /auth/refresh` (리프레시 토큰 쿠키) |
| 공통 로그아웃 | `LogoutUseCase` | `SessionService` | `POST /auth/logout` → 항상 204, 쿠키 만료. 그 리프레시 토큰 하나만 지운다. 액세스 토큰은 만료까지 통과하지만 브라우저는 쿠키와 함께 버린다 |
| 흐름 5 내 프로필 | `GetMyProfileQuery`, `RenameProfileUseCase` | `ProfileService` | `GET`, `PATCH /me/profile` |
| 사람 확인 | `CheckHumanUseCase` | `HumanCheckService` | 가입 시작, 비밀번호 로그인, 코드 로그인 시작, 재설정 링크 요청의 컨트롤러가 유스케이스보다 먼저 부른다 |
| 요청 인증 | `AuthenticateAccessTokenQuery` (`member-api`) | `AccessTokenAuthenticationService` | `apps/api`의 필터가 호출 |
| 다른 컨텍스트에 계정 상태 공개 | `GetAccountStatusQuery` (`member-api`) | `AccountStatusQueryService` | 출판의 번역 계층이 호출. 자기 읽기 전용 트랜잭션에서 동작하고, 탈퇴(DELETED)는 `CLOSED`로 내보낸다 |

### 구현에서 정한 것

| 항목 | 내용 |
|---|---|
| 시도의 세션 묶기 | 시작 응답의 시도 ID를 완료 요청에 함께 내야 한다. ID를 가진 요청자만 코드를 검증할 수 있다 |
| 가입 시도와 로그인 시도 | 만료, 실패 횟수, 해시 규칙은 `EmailCodeAttempt` 하나에 있고 두 시도가 상속한다. 테이블은 `signup_attempts`와 `login_attempts`로 나눠, 한 목적으로 시작한 시도를 다른 목적으로 완료할 수 없다 |
| 없는 주소의 코드 로그인 | 시도를 저장하지 않고 새 ID만 돌려준다. 응답 모양은 있는 주소와 같다. 그 ID로 완료하면 만료된 시도와 같은 `VERIFICATION_EXPIRED`다 |
| 코드 로그인과 계정 상태 | ACTIVE가 아니면 코드가 맞아도 403 `ACCOUNT_NOT_ACTIVE`다. 이 거부는 예외라서 시도 삭제도 함께 되돌아가고, 시도는 만료될 때까지 남는다. 다시 내도 같은 거부다 |
| 코드 메일 | 제목으로 가입 코드와 로그인 코드를 구분하고, 요청하지 않았다면 코드를 알려주지 말라고 적는다 |
| 실패 횟수 | 틀린 코드의 실패 횟수를 커밋한 뒤 실패를 알린다. 예외로 알리면 트랜잭션과 함께 횟수가 되돌아간다 |
| 코드와 토큰 저장 | 원값을 저장하지 않는다. 코드는 시도 ID를 섞은 SHA-256, 리프레시 토큰은 SHA-256 |
| 액세스 토큰 | HS256 JWT, 15분. 정지된 계정의 토큰도 만료 전까지는 통과한다. 리프레시는 거부된다 |
| 리프레시 토큰 | 사용할 때마다 지우고 새로 발급한다. 같은 토큰으로 동시에 갱신하면 하나만 성공한다(유예 구간 없음) |
| 표시 이름 생성 | 가입 시 생성기 이름이 이미 쓰이면 다시 뽑는다(5회). 동시에 같은 이름을 뽑은 두 가입 중 하나는 고유 제약에 걸려 실패한다. 확률이 낮아 받아들인다 |
| 비밀번호 길이 | 8자 이상 72자 이하. BCrypt는 72바이트까지만 읽는다 |
| 코드 발송 | `contentria.member.mail.mode`가 `smtp`면 메일, `log`면 로그(로컬 전용) |

### 소셜 로그인에서 정한 것

| 항목 | 내용 |
|---|---|
| 공급자 | 구글만. 다른 공급자는 01의 확인 필요 항목을 푼 뒤 `AuthProvider`와 클라이언트 등록을 추가한다 |
| 흐름 2의 1번 (서명 검증) | Spring Security OAuth2 로그인이 한다. 상태(state), OIDC nonce, PKCE(S256)를 쓰고, ID 토큰의 서명(JWKS), 발급자, 대상, 만료, nonce를 확인한다 |
| 읽는 클레임 | ID 토큰의 `sub`, `email`, `email_verified`만 읽는다. userinfo 엔드포인트는 부르지 않는다(`setRetrieveUserInfo { false }`). `email_verified`가 없거나 `true`가 아니면 `false`로 본다 |
| 진행 중인 인가 요청 | API에 HTTP 세션이 없으므로 HttpOnly 쿠키 `oauth2AuthRequest`(10분)에 담는다. 액세스 토큰 비밀키에서 용도별로 유도한 키로 HMAC-SHA256 서명한다. 서명이 없으면 연동 대상 계정을 바꿔 자기 소셜 신원을 남의 계정에 붙일 수 있다 |
| 로그인과 연동 구분 | 같은 콜백으로 돌아오므로 인가 요청에 의도를 담는다. `?intent=link`로 시작하면 그 순간 로그인한 계정 ID도 함께 담는다. 로그인하지 않고 시작한 연동은 콜백에서 `SIGN_IN_REQUIRED`로 거부한다 |
| 돌아갈 주소 | `contentria.member.social.*`에 설정한 주소로만 보낸다. 요청에서 읽지 않는다. 실패는 `?error=<코드>`로 알리고 예외 메시지는 싣지 않는다 |
| 실패 코드 | `SOCIAL_EMAIL_MISSING`(2의 3번), `SOCIAL_EMAIL_NOT_VERIFIED`(2의 4번), `ACCOUNT_NOT_ACTIVE`(2의 2, 6번), `PROVIDER_ALREADY_LINKED`(3의 2번, 이미 다른 구글 신원이 붙은 계정의 자동 연동), `SOCIAL_IDENTITY_TAKEN`(3의 1번), `SIGN_IN_REQUIRED`, 그 밖은 `SOCIAL_LOGIN_FAILED` |
| 공급자 토큰 | 로그인 뒤에는 쓰지 않으므로 저장하지 않는다. Spring Security 기본 저장소는 로그인마다 메모리에 쌓는다 |
| 연동 해제 범위 | 소셜 공급자만. `/me/credentials/email`은 404다. 비밀번호를 지우는 경로는 없다 |
| 소셜 신원으로 계정 찾기 | `Account`의 named query `Account.findBySocialIdentity`. `(provider, provider_id)` 고유 제약이 신원 하나에 계정 하나를 보증한다 |

### 계정 관리에서 정한 것

| 항목 | 내용 |
|---|---|
| 코드 확인 공통 | 가입, 코드 로그인, 이메일 변경, 탈퇴가 `runCodeChecked`와 `recordOn`을 함께 쓴다. 틀린 코드는 커밋한 뒤 실패를 알린다 |
| 비밀번호 링크 토큰 | 32바이트 난수. SHA-256 해시만 `password_tokens`에 저장한다. 30분, 1회용, 계정에 묶인다. 새 링크를 요청하거나 비밀번호가 바뀌면 그 계정의 토큰을 모두 지운다 |
| 링크 주소 | `contentria.member.password.link-url`의 프론트엔드 페이지에 `#token=`으로 붙인다. 프래그먼트는 서버로 가지 않아 접근 로그와 Referer에 남지 않는다 |
| 설정 링크와 변경의 구분 | 비밀번호가 없는 계정의 변경은 409 `PASSWORD_NOT_SET`, 있는 계정의 설정 링크 요청은 409 `PASSWORD_ALREADY_SET`이다. 틀린 현재 비밀번호는 400 `CURRENT_PASSWORD_MISMATCH`라서 프론트엔드가 세션 만료(401)와 헷갈리지 않는다 |
| 분실 링크 | 없는 주소와 ACTIVE가 아닌 계정에는 보내지 않고, 응답은 늘 202다. 링크로 비밀번호를 정하면 그 브라우저에 새 세션을 준다. 코드 로그인과 같은 수준의 증명(주소 통제)이기 때문이다 |
| 이메일 변경의 두 코드 | 시도 하나에 두 코드를 이어 붙인 값의 해시를 저장한다(`email_change_attempts`, `email`은 새 주소). 둘 중 하나만 틀려도 한 번의 실패이고, 5회 제한이 쌍에 걸린다 |
| 이메일 변경과 다른 계정 | 시작할 때 다른 계정이 쓰는 주소면 409 `EMAIL_IN_USE`다. 가입과 달리 가입 수단은 알려주지 않는다. 확정할 때 다시 확인하고, 동시에 같은 주소로 바꾸는 경합은 `accounts.email` 고유 제약이 막는다 |
| 시도의 소유 | 이메일 변경과 탈퇴의 시도는 계정 ID를 담는다. 다른 계정이 그 시도 ID로 완료하면 없는 시도처럼 `VERIFICATION_EXPIRED`다 |
| 관리 유스케이스의 계정 상태 | 액세스 토큰은 정지 뒤에도 만료까지 통과하므로, 계정을 바꾸는 유스케이스는 상태를 다시 확인한다(`loadActive`). 정지된 계정은 403 `ACCOUNT_NOT_ACTIVE`다 |
| 탈퇴 뒤 정리 | 탈퇴 시도, 이메일 변경 시도, 옛 주소의 로그인 시도, 비밀번호 토큰, 리프레시 토큰을 모두 지운다. 응답은 두 세션 쿠키를 만료시킨다 |

### 사람 확인에서 정한 것

| 항목 | 내용 |
|---|---|
| 공급자 | Google reCAPTCHA. 보이지 않는 확인은 v3, 체크박스는 v2이고 비밀키가 각각이다. `adapter/required/captcha/RecaptchaVerifier`가 `siteverify`를 JDK `HttpClient`로 부른다(5초) |
| 나누기 | 어댑터(`HumanVerifier`)는 공급자의 답(점수, 동작, 받아들임)만 돌려준다. 기준 점수와 동작 비교는 `HumanCheckService`가 한다 |
| 요청 | 네 요청에 `recaptchaV3Token`, `recaptchaV2Token`(둘 다 선택, 4096자까지)을 더했다. 동작 이름은 `sign_up`, `login`, `login_code`, `password_reset`이다. 화면도 같은 이름으로 v3 토큰을 만든다 |
| 응답 | 체크박스 요구는 403 `RECAPTCHA_CHALLENGE_REQUIRED`, 체크박스 실패는 403 `RECAPTCHA_FAILED`, 공급자 장애는 503 `RECAPTCHA_UNAVAILABLE` |
| 동작 비교 | 공급자가 동작을 알려 줄 때만 비교한다. v3 토큰에는 늘 동작이 있다. 동작이 없는 답(v2 토큰을 v3로 보낸 경우)은 점수도 없어서 어차피 통과하지 못한다 |
| 설정 | `contentria.member.recaptcha`: `mode`(`google`, 로컬 개발만 `off`), `v3-secret-key`, `v2-secret-key`, `minimum-score`(0.5). `google`인데 비밀키가 비면 뜨지 않는다 |
| 레거시와 다른 점 | 레거시는 기준 점수가 1.0이라 거의 언제나 체크박스를 띄웠다. 여기서는 0.5에서 시작하고 설정으로 바꾼다 |
| 테스트 | `apps/api`는 `FakeHumanVerifier`로 Google을 대신한다. 토큰 `human`은 통과, `bot`은 점수가 낮다 |

### 아직 없는 것

| 항목 | 상태 |
|---|---|
| 코드 로그인 응답 시간 차이. 있는 주소만 메일을 보내므로 응답이 늦다 | 범위 밖. 가입이 이미 가입 여부를 알려주므로([01, 가입은 가입 여부를 알려준다](01-rules.md#가입은-가입-여부를-알려준다)) 이것만 막아서는 얻는 것이 없다 |
| 정지와 해제 API, 정지 시 세션 무효화 | 관리자 기능과 함께 정한다. 도메인 메서드(`suspend`, `reinstate`)만 있다 |
| 무효화 직후의 액세스 토큰 차단 | 범위 밖. 최대 15분을 받아들인다. 막으려면 토큰마다 서버 조회가 필요하다 |
| 속도 제한, 리프레시 토큰 유예 구간, 만료된 시도와 토큰 정리 배치 | 범위 밖. 필요할 때 따로 정한다. 코드 메일 폭주는 지금 사람 확인만 막는다 |

## 현재 코드와의 차이

없음. 3단계(도메인 정렬)와 4단계(스키마)에서 모두 반영했다.
