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
| 소셜 연동됨 | 계정 이메일로 안내 메일 |
| 소셜 연동 해제됨 | 계정 이메일로 안내 메일 |
| 비밀번호 변경됨 | 안내 메일, 다른 세션 무효화 |
| 이메일 변경 요청됨 | 기존 주소로 알림, 새 주소로 확인 코드 |
| 이메일 변경 확정됨 | 다른 세션 무효화 |
| 계정 탈퇴됨 | 다른 세션 무효화 |

메일 발송이 실패해도 트랜잭션을 되돌리지 않는다. 커밋 후 처리한다.

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
| 세션 갱신 | `RefreshSessionUseCase` | `SessionService` | `POST /auth/refresh` (리프레시 토큰 쿠키) |
| 흐름 5 내 프로필 | `GetMyProfileQuery`, `RenameProfileUseCase` | `ProfileService` | `GET`, `PATCH /me/profile` |
| 요청 인증 | `AuthenticateAccessTokenQuery` (`member-api`) | `AccessTokenAuthenticationService` | `apps/api`의 필터가 호출 |

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

### 아직 없는 것

| 항목 | 하위 단계 |
|---|---|
| 소셜 로그인, 자동 연동, 수동 연동과 해제 (흐름 2, 3) | 5c |
| 비밀번호 설정과 재설정, 이메일 변경, 탈퇴와 프로필 탈퇴 표시 (흐름 4) | 5d |
| 코드 로그인 응답 시간 차이. 있는 주소만 메일을 보내므로 응답이 늦다 | 범위 밖. 가입이 이미 가입 여부를 알려주므로([01, 가입은 가입 여부를 알려준다](01-rules.md#가입은-가입-여부를-알려준다)) 이것만 막아서는 얻는 것이 없다 |
| 속도 제한, reCAPTCHA, 세션 무효화, 리프레시 토큰 유예 구간, 만료된 시도와 토큰 정리 배치 | 범위 밖. 필요할 때 따로 정한다 |

## 현재 코드와의 차이

없음. 3단계(도메인 정렬)와 4단계(스키마)에서 모두 반영했다.
