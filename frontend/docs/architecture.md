# 프론트엔드 구조

화면 코드를 기술 종류(`actions/`, `components/`, `hooks/`, `types/`)가 아니라 기능으로 나눈다. 기능은 백엔드의 바운디드 컨텍스트와 1:1로 맞춘다([backend/docs/strategic/03-bounded-contexts.md](../../backend/docs/strategic/03-bounded-contexts.md)).
한 기능을 고칠 때 한 폴더 안에서 끝나게 하고, 백엔드의 컨텍스트가 바뀌면 같은 이름의 기능 폴더만 보면 되게 하려는 것이다.

## 폴더

```
src/
├── app/                 라우팅과 페이지. 여러 기능을 모아 화면을 만든다
│   └── _composition/    여러 기능의 API를 모으는 서버 함수 (라우트가 아니다)
├── features/
│   └── <기능>/
│       ├── api/         Server Actions와 서버에서 부르는 함수. 백엔드 API를 부르는 곳은 여기뿐이다
│       ├── types/       그 기능의 API 요청과 응답 타입
│       ├── hooks/       TanStack Query 훅, 화면 상태 훅
│       └── components/  그 기능의 컴포넌트
├── shared/
│   ├── api/             apiServer, ApiError, ActionResult, 세션 쿠키 전달
│   ├── ui/              shadcn/ui 컴포넌트
│   ├── components/      어느 기능에도 속하지 않는 컴포넌트 (표, 페이지 나누기, 입력)
│   ├── providers/       React Query 공급자
│   ├── lib/             cn(), 날짜 형식, QueryClient
│   └── constants/       화면 경로
└── proxy.ts             요청마다 세션 쿠키를 보고 토큰 갱신과 보호 경로를 처리한다
```

기능 안의 하위 폴더는 필요할 때 만든다. 빈 폴더를 미리 두지 않는다.

| 기능 | 백엔드 컨텍스트 | 담는 것 |
|---|---|---|
| `member` | member | 가입, 로그인, 세션, 프로필, 계정 설정 |
| `publishing` | publishing | 블로그, 카테고리, 글, 편집기, 공개 블로그와 글 |
| `interaction` | interaction | 구독, 좋아요, 댓글, 수 |
| `notification` | notification | 알림함 |
| `media` | media | 이미지와 영상 올리기, 영상 재생 |
| `analytics` | analytics | 방문 기록, 대시보드 |

## 규칙

| 규칙 | 이유 | 확인 |
|---|---|---|
| 기능은 다른 기능의 코드를 import하지 않는다 | 기능 하나를 고칠 때 다른 기능이 따라 깨지지 않게 한다. 백엔드의 `-core`끼리 의존하지 않는 것과 같다 | ESLint `no-restricted-imports` |
| 여러 기능을 모으는 일은 `app/`의 페이지와 레이아웃이 한다 | 글 상세에 좋아요와 댓글을 붙이는 일처럼 기능을 넘는 조합을 한곳에 둔다. 백엔드 `apps/api`의 `composition`과 같다 | 리뷰 |
| `shared/`는 기능과 `app/`을 모른다 | 공용 코드가 특정 기능에 묶이지 않게 한다 | ESLint `no-restricted-imports` |
| 기능과 `shared/` 밖을 가리킬 때는 `@/`로 import한다 | lint 규칙이 경로 문자열을 보고 판단한다. `../../`로 기능 밖에 나가면 규칙이 보지 못한다 | 리뷰 |
| 백엔드 API는 각 기능의 `api/`에서 `shared/api/apiServer`로만 부른다 | 주소, 인증 헤더, 오류 형식을 한곳에서 다룬다 | 리뷰 |

lint 규칙은 `eslint.config.js`에 있다. `src/features` 아래 폴더를 읽어 기능마다 경계를 만든다. 기능을 더할 때 설정을 고칠 필요가 없다.

## 조합

여러 컨텍스트의 데이터를 한 화면에 모으는 일은 두 곳에서 한다.

| 위치 | 언제 | 예 |
|---|---|---|
| 백엔드 `apps/api`의 조합 API | 권한이나 공개 여부를 확인한 뒤에만 다른 컨텍스트의 데이터를 줄 수 있을 때 | 블로그 대시보드(소유자 확인), 글의 영상(공개 글 확인) |
| `app/_composition/`의 서버 함수 | 모두 공개 데이터를 화면에 맞게 모으기만 할 때 | 블로그 레이아웃(블로그, 카테고리, 소유자 프로필), 글 목록과 상세의 작성자 |
| `app/` 아래 `_components/`의 클라이언트 컴포넌트 | 한 기능의 화면에 다른 기능의 동작(함수)을 끼워 넣을 때. 서버 컴포넌트는 함수를 넘기지 못한다 | 글 편집기(출판)에 이미지 올리기와 영상 칸(미디어)을 넣는 `PostEditor` |

`app/_composition/`은 각 기능의 `api/`를 불러 합친다. 목록에서는 작성자 ID를 모아 회원 프로필을 한 번에 조회하고, 글마다 부르지 않는다. 한 요청 안에서 여러 번 쓰는 조합(레이아웃, 페이지, 사이드바가 모두 부르는 블로그 레이아웃)은 React `cache`로 한 번만 부른다.

## API 호출

| 항목 | 내용 |
|---|---|
| 주소 | `NEXT_PUBLIC_API_BASE_URL` + `/api/...`. 백엔드의 context path가 `/api`다 |
| 인증 | 브라우저의 `accessToken` 쿠키를 읽어 `Authorization: Bearer`로 보낸다. 백엔드는 헤더와 쿠키를 둘 다 읽는다 |
| 오류 | 백엔드의 모든 컨텍스트가 `{code, message}`로 답한다. `ApiError.from(status, body)`가 이것을 `ApiError`로 바꾼다. 코드는 `INVALID_REQUEST`, `POST_NOT_FOUND` 같은 이름이다. 모양이 다른 본문(게이트웨이 오류 등)은 `UNKNOWN_ERROR`다 |
| Server Action의 결과 | 예외를 브라우저로 넘길 수 없어 `ActionResult<T>`로 돌려준다. 실패는 `toActionError(error)`로 만든다 |
| 세션 쿠키 | 백엔드는 로그인, 갱신, 로그아웃 때 세션 쿠키를 `Set-Cookie`로 직접 심고, 토큰을 본문에 싣지 않는다. Server Action은 Next.js 서버에서 백엔드를 부르므로, 그 `Set-Cookie`를 브라우저 응답에 옮겨 실어야 한다. `apiServer`에 `relaySessionCookies: true`를 주면 `accessToken`, `refreshToken` 두 쿠키만 옮긴다 |
| 리프레시 토큰 | 서버에서 부르는 요청에는 브라우저의 쿠키가 실리지 않는다. 백엔드가 리프레시 토큰을 읽는 요청(로그아웃)은 `sendRefreshToken: true`로 넘긴다. `proxy.ts`의 갱신은 직접 넘긴다 |

배포에서는 웹과 API가 같은 호스트에 있고 `/api`만 백엔드로 간다. 로컬에서는 포트가 다르지만 쿠키는 포트를 가리지 않는다. 그래서 두 환경 모두 백엔드가 심은 쿠키를 Next.js가 그대로 읽는다.

## 테스트

React와 Next.js 실행 환경이 필요 없는 코드(`shared/api`의 파싱, 변환 같은 것)는 Vitest로 확인한다. `src/**/*.test.ts`가 대상이다.

```bash
npm test
```

## 옮기는 중

새 백엔드 API로 옮기면서 기능 하나씩 `features/`로 옮긴다. 옮기기 전의 코드는 예전 폴더(`actions/`, `components/<기능>/`, `hooks/`, `types/api/`, `lib/`, `store/`, `constants/`)에 남아 있다.

| 단계 | 내용 | 상태 |
|---|---|---|
| F0 | `shared/`, 경계 lint 규칙, 새 오류 형식, 세션 쿠키 전달, 테스트 도구 | 완료 |
| F1 | member: 가입, 로그인, 토큰 갱신, 로그아웃, 프로필, reCAPTCHA | 완료 |
| F2 | publishing: 블로그, 카테고리, 글, 쪽 번호 목록, 공개 화면의 조합 | 완료 |
| F3 | media: 이미지 3단계 올리기, 영상 올리기와 재생 | 완료 |
| F4 | analytics: 방문 기록, 대시보드 | 완료 |
| F5 | 새 화면: 구독, 좋아요와 수, 댓글 | 완료 |
| F5 | 새 화면: 알림함 | |
| F5 | 새 화면: 계정 설정 | |

여러 기능을 함께 보여 주는 컴포넌트(헤더, 대시보드의 프로필 카드와 사이드바)는 아직 예전 `components/`에 있다. 기능을 옮기는 동안에는 그대로 두고, 마지막에 `app/` 아래로 옮긴다. 예전 폴더가 모두 비면 이 절을 지운다.
