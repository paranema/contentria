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
| 인증 | HttpOnly 쿠키, axios-auth-refresh, reCAPTCHA v3와 v2 |

## 디렉터리 구조

```
src/
├── app/                        App Router
│   ├── (home)/                 첫 화면, 약관
│   ├── (auth)/                 로그인, 회원가입, 소셜 로그인 콜백
│   ├── dashboard/
│   │   ├── (main)/             대시보드, 글 목록, 카테고리, 설정
│   │   ├── (editor)/           글 편집기 (새 글, 고치기)
│   │   └── (onboarding)/       블로그 만들기 안내
│   └── user/[blogSlug]/        공개 블로그 (사이드바는 parallel route)
├── actions/                    Server Actions. 백엔드 API를 부른다
├── components/
│   ├── ui/                     shadcn/ui 기본 컴포넌트
│   ├── common/                 공용 컴포넌트 (페이지 나누기, 표, 입력)
│   ├── auth/                   로그인, 회원가입, reCAPTCHA
│   ├── blog/                   공개 블로그 (글 카드, 사이드바, 목차, 영상 플레이어)
│   ├── dashboard/              대시보드 (편집기, 카테고리, 통계, 설정)
│   ├── home/                   첫 화면
│   └── analytics/              방문 기록
├── hooks/
│   ├── queries/, mutations/    TanStack Query 훅
│   └── useLoginFlow 등         여러 단계로 된 로그인과 회원가입의 상태
├── lib/
│   ├── apiClient.ts            브라우저용 Axios 클라이언트 (토큰 갱신)
│   ├── apiServer.ts            서버용 fetch 래퍼 (쿠키의 토큰을 붙인다)
│   ├── uploadImage.ts          이미지를 압축해 R2에 바로 올린다
│   ├── uploadVideo.ts          영상을 R2에 바로 올린다 (진행률 표시)
│   └── markdown/, schemas/     마크다운 플러그인, Zod 스키마
├── store/                      Zustand 스토어 (인증, UI)
├── types/                      API 요청과 응답 타입, 환경 변수 타입
├── constants/                  경로, 오류 코드, 메시지
└── proxy.ts                    요청마다 쿠키를 보고 토큰 갱신과 보호 경로를 처리한다
```

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
| 서버 컴포넌트, Server Actions | `apiServer.ts` | 쿠키에서 액세스 토큰을 읽어 `Authorization` 헤더로 붙인다. 401이면 로그인 화면으로 보낸다 |
| 브라우저 컴포넌트 | TanStack Query 훅 → `apiClient.ts` | 액세스 토큰을 붙이고 오류 모양을 맞춘다. 401이면 axios-auth-refresh가 토큰을 갱신하고 다시 보낸다. 동시에 실패한 요청은 줄을 세운다 |

### 인증

| 단계 | 위치 | 하는 일 |
|---|---|---|
| 1 | `proxy.ts` | 화면을 옮길 때마다 쿠키를 본다. 액세스 토큰이 없고 리프레시 토큰이 있으면 서버에서 갱신하고 쿠키를 새로 심는다. 같은 리프레시 토큰의 갱신은 한 번으로 묶는다. 토큰 없이 `/dashboard`에 오면 로그인 화면으로 보낸다 |
| 2 | `apiServer.ts` | 서버에서 부른 API가 401이면 로그인 화면으로 보낸다 |
| 3 | `apiClient.ts` | 브라우저에서 부른 API가 401이면 갱신하고 다시 보낸다 |

토큰은 HttpOnly 쿠키에 둔다. 액세스 토큰은 15분, 리프레시 토큰은 7일이다.

### 파일 올리기

이미지와 영상은 백엔드를 거치지 않고 브라우저가 Cloudflare R2에 바로 올린다. 백엔드에서 미리 서명된 업로드 주소를 받고, 그 주소로 `PUT` 한다. 이미지는 올리기 전에 브라우저에서 압축한다.

## 빌드와 실행

```bash
npm ci
npm run dev        # 개발 서버 (Turbopack), http://localhost:3000
npm run build      # 운영 빌드
npm start
npm run lint       # ESLint와 Prettier
```

lint와 formatting 규칙은 [docs/lint-and-formatting.md](docs/lint-and-formatting.md)에 있다.

### 환경 변수

`.env.sample`을 `.env.local`로 복사해 채운다.

| 변수 | 내용 |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | 서버가 부르는 백엔드 주소. 배포에서는 클러스터 안의 주소다. 비우면 `http://localhost:8080` |
| `NEXT_PUBLIC_EXTERNAL_API_BASE_URL` | 브라우저가 가는 백엔드의 공개 주소. Google 로그인 시작에 쓴다 |
| `NEXT_PUBLIC_RECAPTCHA_V3_SITE_KEY` | reCAPTCHA v3 사이트 키 |
| `NEXT_PUBLIC_RECAPTCHA_V2_CHECKBOX_SITE_KEY` | reCAPTCHA v2 체크박스 사이트 키. 로그인 중 백엔드가 추가 확인을 요구하면(403) 띄운다 |
| `NEXT_PUBLIC_CDN_BASE_URL` | 이미지를 내주는 R2 공개 주소. `next/image`가 허용할 호스트가 된다 |
| `COOKIE_SECURE` | HTTPS에서는 `true`, 로컬 HTTP에서는 `false` |

## 컨테이너 이미지

`Dockerfile`은 의존성 설치, 빌드, 실행을 나눈 멀티 스테이지 빌드다. `standalone` 출력만 담고, root가 아닌 사용자로 `node server.js`를 띄운다. 포트는 3000이다.

```bash
docker build -t contentria/blog-frontend:<태그> .
```

배포는 `infra/gitops/03-services/blog-frontend-web`의 매니페스트를 ArgoCD가 맞춘다.
