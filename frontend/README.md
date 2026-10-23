# Contentria Frontend

Contentria의 웹 화면이다. Next.js App Router로 만들고, 공개 블로그와 글은 서버에서 렌더링해 검색에 노출하고, 대시보드와 편집기는 브라우저에서 동작한다.

> 지금 화면은 레거시 백엔드 API(`/api/auth/*`, `/api/blogs/*`, `/api/videos/presigned` 등)에 맞춰져 있다. 새 백엔드(`backend/apps/api`)의 API로 옮기는 작업은 남아 있다.

## 기술 스택

| 분류 | 기술 |
|---|---|
| 프레임워크 | Next.js 16 (App Router, Turbopack, `output: 'standalone'`) |
| 언어 | TypeScript 5, React 19 |
| 스타일 | Tailwind CSS 3, tailwindcss-animate, @tailwindcss/typography |
| UI 컴포넌트 | shadcn/ui (Radix UI) |
| 서버 상태 | TanStack Query 5 |
| 클라이언트 상태 | Zustand 5 |
| 폼과 검증 | React Hook Form, Zod 4 |
| 글 편집 | MDXEditor (코드 블록 강조는 CodeMirror) |
| 글 표시 | react-markdown, remark-gfm, remark-directive(안내 상자), rehype-sanitize |
| 영상 재생 | hls.js |
| 차트, 끌어 놓기 | Recharts, dnd-kit |
| 인증 | HttpOnly 쿠키, reCAPTCHA v3와 v2 |
| 테스트 | Vitest |

## 디렉터리 구조

기능 폴더(`features/<기능>/`, 기능은 백엔드 컨텍스트와 1:1)와 공용 코드(`shared/`)로 나눈다. 폴더의 역할, 기능 사이의 규칙, API 호출 방식은 [docs/architecture.md](docs/architecture.md)에 있다.

```
src/
├── app/            라우팅과 페이지. 여러 기능을 모아 화면을 만든다
├── features/       기능마다 api, types, hooks, components
├── shared/         apiServer와 오류, shadcn/ui, 공용 컴포넌트와 유틸
└── proxy.ts        요청마다 세션 쿠키를 보고 토큰 갱신과 보호 경로를 처리한다
```

새 백엔드 API로 옮기면서 기능 하나씩 `features/`로 옮기는 중이다. 옮기기 전의 코드는 예전 폴더(`actions/`, `components/`, `hooks/`, `types/`, `lib/`, `store/`, `constants/`)에 있다.

## 구조

### 렌더링

| 경로 | 렌더링 | 이유 |
|---|---|---|
| `/@블로그주소`, `/@블로그주소/글주소` | 서버 | 검색 노출, 첫 화면 속도 |
| `(home)` | 서버 | 검색 노출 |
| `dashboard/*` | 레이아웃은 서버, 내용은 브라우저 | 회원과 블로그 정보는 서버에서 받고, 편집과 조작은 브라우저에서 한다 |
| `(auth)/*` | 브라우저 | 여러 단계로 된 폼이라 브라우저의 상태가 필요하다 |

공개 블로그 주소는 `next.config.ts`의 rewrite로 바꾼다.

```
/@블로그주소            →  /user/[blogSlug]
/@블로그주소/글주소     →  /user/[blogSlug]/posts/[postSlug]
```

### 데이터 가져오기

| 쪽 | 경로 | 하는 일 |
|---|---|---|
| 서버 컴포넌트, Server Actions | `shared/api/apiServer.ts` | 쿠키에서 액세스 토큰을 읽어 `Authorization` 헤더로 붙인다. 401이면 로그인 화면으로 보낸다. 로그인처럼 백엔드가 세션 쿠키를 심는 요청은 그 쿠키를 브라우저로 옮긴다 |
| 브라우저 컴포넌트 | TanStack Query 훅 → Server Actions | 브라우저에서 백엔드를 직접 부르지 않는다. 훅이 Server Action을 부른다 |

### 인증

| 단계 | 위치 | 하는 일 |
|---|---|---|
| 1 | 로그인, 가입 (`features/member`) | 백엔드가 세션 쿠키를 `Set-Cookie`로 심는다. Server Action이 그 쿠키를 브라우저 응답으로 옮긴다 |
| 2 | `proxy.ts` | 화면을 옮길 때마다 쿠키를 본다. 액세스 토큰이 없고 리프레시 토큰이 있으면 백엔드에서 갱신하고, 백엔드가 준 두 쿠키를 그대로 심는다. 같은 리프레시 토큰의 갱신은 한 번으로 묶는다. 토큰 없이 `/dashboard`에 오면 로그인 화면으로 보낸다 |
| 3 | `apiServer.ts` | 서버에서 부른 API가 401이면 로그인 화면으로 보낸다 |
| 4 | 로그아웃 | 리프레시 토큰을 백엔드에 넘겨 그 세션을 지우고, 만료된 쿠키를 브라우저로 옮긴다 |

토큰은 HttpOnly 쿠키에 둔다. 쿠키의 속성(수명 15분과 7일, `Secure`, `SameSite=Lax`)은 백엔드가 정한다.

가입, 비밀번호 로그인, 코드 로그인 시작, 비밀번호 재설정 링크 요청은 reCAPTCHA v3 토큰을 함께 보낸다. 점수가 낮으면 백엔드가 403 `RECAPTCHA_CHALLENGE_REQUIRED`로 답하고, 화면은 v2 체크박스를 띄워 다시 보낸다. 사이트 키가 없으면(로컬) 토큰 없이 보내고, 이때 백엔드는 `MEMBER_RECAPTCHA_MODE=off`여야 한다.

### 파일 올리기

이미지와 영상은 백엔드를 거치지 않고 브라우저가 Cloudflare R2에 바로 올린다(`features/media/upload`). 백엔드에서 미리 서명된 업로드 주소를 받고, 그 주소로 `PUT` 한다. 이미지는 올리기 전에 브라우저에서 압축하고, 올린 뒤 완료를 알린다. 백엔드가 저장된 파일의 형식을 확인하고 사진 정보를 지운 뒤에야 본문에 쓸 수 있다. 영상은 올리면 worker가 HLS로 변환하고, 글 화면은 변환 중이면 안내를, 끝나면 플레이어를 보인다.

## 빌드와 실행

```bash
npm ci
npm run dev        # 개발 서버 (Turbopack), http://localhost:3000
npm run build      # 운영 빌드
npm start
npm run lint       # ESLint와 Prettier, 기능 경계 규칙
npm test           # Vitest
```

lint와 formatting 규칙은 [docs/lint-and-formatting.md](docs/lint-and-formatting.md)에 있다.

### 환경 변수

`.env.sample`을 `.env.local`로 복사해 채운다.

| 변수 | 내용 |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | 서버가 부르는 백엔드 주소. 배포에서는 클러스터 안의 주소다. 비우면 `http://localhost:8080` |
| `NEXT_PUBLIC_EXTERNAL_API_BASE_URL` | 브라우저가 가는 백엔드의 공개 주소. `/api`까지 포함한다(로컬은 `http://localhost:8080/api`). Google 로그인과 설정 화면의 Google 연동 시작에 쓴다 |
| `NEXT_PUBLIC_RECAPTCHA_V3_SITE_KEY` | reCAPTCHA v3 사이트 키 |
| `NEXT_PUBLIC_RECAPTCHA_V2_CHECKBOX_SITE_KEY` | reCAPTCHA v2 체크박스 사이트 키. 로그인 중 백엔드가 추가 확인을 요구하면(403) 띄운다 |
| `NEXT_PUBLIC_CDN_BASE_URL` | 이미지를 내주는 R2 공개 주소. `next/image`가 허용할 호스트가 된다 |

## 컨테이너 이미지

`Dockerfile`은 의존성 설치, 빌드, 실행을 나눈 멀티 스테이지 빌드다. `standalone` 출력만 담고, root가 아닌 사용자로 `node server.js`를 띄운다. 포트는 3000이다.

```bash
docker build -t contentria/blog-frontend:<태그> .
```

배포는 `infra/gitops/03-services/blog-frontend-web`의 매니페스트를 ArgoCD가 맞춘다.
