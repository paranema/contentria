# 인증

토큰을 브라우저 스크립트(`localStorage` 등)에 노출하지 않고, 백엔드가 발급하는 `HttpOnly` 세션 쿠키를 기반으로 인증 상태를 유지합니다.

## 세션 쿠키

세션은 `accessToken`과 `refreshToken` 두 개의 `HttpOnly` 쿠키로 구성되며, 만료 시간과 `Secure`·`SameSite=Lax` 속성은 모두 백엔드에서 결정합니다.

| 작업 | `apiServer` 옵션 | 동작 방식 |
| --- | --- | --- |
| 일반 인증 요청 | `requireAuth: true` (기본값) | 브라우저 요청 쿠키의 `accessToken`을 읽어 백엔드로 `Authorization: Bearer` 헤더를 전달합니다. |
| 로그인·회원가입·로그아웃 | `relaySessionCookies: true` | 백엔드가 응답 `Set-Cookie` 헤더로 발급하거나 만료시킨 `accessToken`, `refreshToken` 쿠키를 파싱해 브라우저 응답 쿠키에 그대로 반영합니다. |
| 로그아웃 | `sendRefreshToken: true` | 서버 간 호출에는 브라우저 쿠키가 자동 첨부되지 않으므로, 요청 쿠키의 `refreshToken`을 읽어 백엔드 `Cookie` 헤더로 명시 전달합니다. |

운영 환경에서는 웹과 API가 동일 도메인 뒤에 배포되고, 로컬 개발 환경에서는 포트만 다르므로(쿠키는 포트를 구분하지 않음) 두 환경 모두 동일한 쿠키 구조를 사용합니다.

## 요청 프록시

`src/proxy.ts`는 정적 자산과 프리페치 요청을 제외한 페이지 이동 요청마다 실행되어 세션 갱신과 경로 접근 제어를 수행합니다.

1. **토큰 자동 갱신**: `accessToken` 쿠키가 만료되어 없고 `refreshToken`만 남아 있으면 백엔드 `/api/auth/refresh`를 호출해 새 세션 쿠키 쌍을 발급받습니다. 갱신에 성공하면 새 쿠키를 설정한 뒤 현재 URL로 리다이렉트(`307`)해 이후 서버 컴포넌트가 새 토큰으로 렌더링되도록 합니다.
2. **중복 갱신 병합**: 동일 프로세스 내에서 같은 `refreshToken`으로 동시 요청이 들어오면 `inflightRefresh` 맵으로 진행 중인 갱신 Promise를 공유해 백엔드 중복 호출을 줄입니다. 다중 파드 환경에서의 경합은 백엔드의 리프레시 토큰 유예 구간([refresh-token-grace-window.md](../../../backend/docs/design-notes/refresh-token-grace-window.md))이 최종 방어합니다.
3. **보호 경로 제어**: 미인증 상태(갱신 실패 포함)로 `/dashboard` 하위 경로에 접근하면 세션 쿠키를 정리하고 `/login?redirect=<원래경로>`로 이동시킵니다.
4. **인증 페이지 역방향 제어**: 이미 세션 쿠키가 있는 사용자가 `/login`이나 `/signup`에 접근하면 `/dashboard`로 리다이렉트합니다. 단, 서버 API 호출 중 `401`이 발생해 `/login?alert=session_expired`로 이동한 경우에는 남아 있는 쿠키를 삭제하고 로그인 화면을 표시합니다.

## 외부 연동

### Google OAuth

Google 로그인과 계정 연동은 브라우저 전체 이동으로 시작되므로 클러스터 내부 주소(`API_BASE_URL`)가 아닌 외부 공개 주소(`EXTERNAL_API_BASE_URL`)를 사용합니다(`features/member/oauth.ts`).

- 로그인 시작: `${externalApiBaseUrl}/oauth2/authorization/google`
- 기존 계정 연동 시작: `${externalApiBaseUrl}/oauth2/authorization/google?intent=link`

### Google reCAPTCHA

회원가입(`sign_up`), 비밀번호 로그인(`login`), 이메일 인증 코드 요청(`login_code`), 비밀번호 재설정 요청(`password_reset`)에는 보이지 않는 reCAPTCHA v3 토큰을 함께 전송합니다.

- 점수가 낮아 백엔드가 `403`(`RECAPTCHA_CHALLENGE_REQUIRED`)을 반환하면 화면에 reCAPTCHA v2 체크박스를 노출해 추가 검증을 진행합니다.
- 로컬 개발 환경에서 사이트 키 환경 변수를 비워 두면 토큰 생성을 건너뛰며, 이때 백엔드는 `MEMBER_RECAPTCHA_MODE=off`로 실행해야 합니다.
