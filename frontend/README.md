# Contentria Frontend

Next.js App Router 기반의 Contentria 웹 프론트엔드 애플리케이션입니다. 공개 블로그와 게시글은 검색 엔진 노출(SEO)을 위해 서버에서 렌더링(SSR)하고, 대시보드와 에디터는 클라이언트에서 동작합니다.

## 기술 스택

| 분류                   | 기술                                                                               |
| ---------------------- | ---------------------------------------------------------------------------------- |
| 프레임워크             | Next.js 16 (App Router, Turbopack, `output: 'standalone'`)                         |
| 언어                   | TypeScript 7 (도구 호환용 API는 TypeScript 6 병행), React 19                       |
| 스타일링               | Tailwind CSS 3, `tailwindcss-animate`, `@tailwindcss/typography`                   |
| UI 컴포넌트            | shadcn/ui (Radix UI)                                                               |
| 서버 상태 관리         | TanStack Query 5                                                                   |
| 폼 및 유효성 검증      | React Hook Form, Zod 4                                                             |
| 마크다운 에디터        | MDXEditor (코드 블록 구문 강조: CodeMirror)                                        |
| 마크다운 렌더링        | `react-markdown`, `remark-gfm`, `remark-directive`(콜아웃 블록), `rehype-sanitize` |
| 영상 스트리밍          | `hls.js`                                                                           |
| 차트 및 드래그 앤 드롭 | Recharts, `dnd-kit`                                                                |
| 인증 및 보안           | HttpOnly 쿠키, Google reCAPTCHA v3·v2                                              |
| 테스트                 | Vitest                                                                             |

## 디렉터리 구조

코드베이스는 백엔드의 바운디드 컨텍스트와 1:1로 대응하는 기능 모듈(`features/<기능>/`)과 공통 모듈(`shared/`)로 구성됩니다. 각 디렉터리의 상세 역할과 모듈 간 의존 규칙, API 호출 방식은 [docs/architecture.md](docs/architecture.md)에 정리되어 있습니다.

```text
src/
├── app/            라우팅 및 페이지 구성 (여러 기능 모듈을 조합하여 화면 구성)
├── features/       도메인 기능별 모듈 (api, types, hooks, components)
├── shared/         apiServer 및 공통 에러 처리, shadcn/ui, 공용 컴포넌트·유틸리티
├── types/          환경 변수 타입 선언
└── proxy.ts        요청마다 세션 쿠키를 확인하여 토큰 갱신 및 보호 경로 접근 제어 수행
```

## 아키텍처 및 주요 설계

### 렌더링 전략

| 경로                                  | 렌더링 방식                       | 설계 이유                                                                         |
| ------------------------------------- | --------------------------------- | --------------------------------------------------------------------------------- |
| `/@블로그주소`, `/@블로그주소/글주소` | 서버 렌더링 (SSR)                 | 검색 엔진 노출(SEO) 및 초기 화면 로딩 속도 확보                                   |
| `(home)`                              | 서버 렌더링 (SSR)                 | 검색 엔진 노출(SEO)                                                               |
| `dashboard/*`                         | 레이아웃: 서버 / 본문: 클라이언트 | 회원·블로그 기본 정보는 서버에서 조회하고, 글 편집과 상호작용은 브라우저에서 처리 |
| `(auth)/*`                            | 클라이언트 렌더링 (CSR)           | 다단계 인증 폼의 클라이언트 상태 관리 필요                                        |

공개 블로그의 `/@<블로그주소>` 경로는 `next.config.ts`의 `rewrites` 설정을 통해 내부 라우트로 매핑됩니다.

```text
/@블로그주소            →  /user/[blogSlug]
/@블로그주소/글주소     →  /user/[blogSlug]/posts/[postSlug]
```

### 데이터 페칭 (Data Fetching)

| 실행 환경                     | 호출 경로                          | 동작 방식                                                                                                                                                                                                                       |
| ----------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 서버 컴포넌트, Server Actions | `shared/api/apiServer.ts`          | 요청 쿠키에서 액세스 토큰을 읽어 `Authorization` 헤더에 추가합니다. 백엔드가 `401`을 반환하면 로그인 페이지로 리다이렉트하며, 로그인처럼 백엔드가 세션 쿠키를 발급하는 요청은 해당 `Set-Cookie`를 브라우저 응답으로 전달합니다. |
| 클라이언트 컴포넌트           | TanStack Query 훅 → Server Actions | 브라우저에서 백엔드 API를 직접 호출하지 않고, TanStack Query 훅이 Server Action을 호출하여 데이터를 주고받습니다.                                                                                                               |

### 인증 및 세션 관리

| 단계 | 처리 위치                           | 동작 방식                                                                                                                                                                                                                                                                                |
| ---- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | 로그인·회원가입 (`features/member`) | 백엔드가 `Set-Cookie` 헤더로 발급한 세션 쿠키를 Server Action이 브라우저 응답으로 그대로 전달합니다.                                                                                                                                                                                     |
| 2    | `proxy.ts`                          | 페이지 이동 시마다 쿠키를 확인합니다. 액세스 토큰이 만료되고 리프레시 토큰이 남아 있으면 백엔드에 토큰 갱신을 요청한 뒤 새 쿠키를 브라우저에 설정합니다(동일 리프레시 토큰에 대한 중복 갱신 요청은 하나로 병합). 미인증 상태로 `/dashboard`에 접근하면 로그인 페이지로 리다이렉트합니다. |
| 3    | `apiServer.ts`                      | 서버에서 호출한 백엔드 API가 `401`을 반환하면 로그인 페이지로 리다이렉트합니다.                                                                                                                                                                                                          |
| 4    | 로그아웃                            | 백엔드에 리프레시 토큰을 전달해 세션을 만료시키고, 만료 처리된 쿠키를 브라우저 응답에 반영합니다.                                                                                                                                                                                        |

- **쿠키 보안 속성**: 인증 토큰은 `HttpOnly` 쿠키에 저장되며, 만료 시간(액세스 토큰 15분, 리프레시 토큰 7일)과 `Secure`, `SameSite=Lax` 속성은 백엔드에서 설정합니다.
- **reCAPTCHA 검증 및 폴백**: 회원가입, 비밀번호 로그인, 이메일 인증 코드 요청, 비밀번호 재설정 링크 요청 시 reCAPTCHA v3 토큰을 함께 전송합니다. 점수가 낮아 백엔드가 `403`(`RECAPTCHA_CHALLENGE_REQUIRED`)을 반환하면 화면에 reCAPTCHA v2 체크박스를 노출하여 추가 인증을 진행합니다. 로컬 개발 환경에서 사이트 키를 비워 두면 토큰 없이 요청을 보내며, 이때 백엔드는 `MEMBER_RECAPTCHA_MODE=off`로 설정되어 있어야 합니다.

### 미디어 업로드 및 스트리밍

이미지와 영상 원본은 백엔드 서버를 거치지 않고 브라우저에서 Cloudflare R2로 직접 업로드합니다(`features/media/upload`).

- **이미지**: 브라우저에서 이미지를 압축한 뒤 백엔드로부터 Presigned URL을 발급받아 R2에 직접 `PUT` 업로드하고 완료를 알립니다. 백엔드에서 실제 파일 형식을 검증하고 위치 정보(EXIF) 등 메타데이터를 제거한 뒤에만 본문에 삽입할 수 있습니다.
- **영상**: Presigned URL을 통해 원본을 R2에 업로드하면 백엔드 워커(`apps/worker`)가 HLS 포맷으로 인코딩합니다. 게시글 화면에서는 변환이 진행 중일 때 상태 안내를 표시하고, 변환이 완료되면 HLS 플레이어를 렌더링합니다.

## 빌드 및 실행

```bash
npm ci
npm run dev        # 로컬 개발 서버 실행 (Turbopack, http://localhost:3000)
npm run build      # 프로덕션 빌드
npm start          # 프로덕션 서버 실행
npm run lint       # ESLint·Prettier 검사 및 기능 모듈 간 경계 규칙 검증
npm run typecheck  # TypeScript 7(tsc) 기반 타입 검사
npm test           # Vitest 단위 테스트 실행
```

린트 및 코드 포매팅 규칙의 상세 내용은 [docs/lint-and-formatting.md](docs/lint-and-formatting.md)에 정리되어 있습니다.

### TypeScript 6·7 병행 구성

TypeScript 7.0(`@typescript/native`)은 네이티브 컴파일러로 전환되면서 외부 도구가 호출하는 기존 JavaScript API를 아직 제공하지 않습니다. 반면 `typescript-eslint`는 이 API가 필요하기 때문에 `package.json`에 두 버전을 함께 구성했습니다.

| 별칭 (Alias)         | 실제 패키지               | 사용처                                                                |
| -------------------- | ------------------------- | --------------------------------------------------------------------- |
| `@typescript/native` | `typescript@7`            | `tsc` CLI 실행 (`npm run typecheck`)                                  |
| `typescript`         | `@typescript/typescript6` | `typescript-eslint`, IDE 언어 서버, `next build` 시 타입 검사(`tsc6`) |

향후 `typescript-eslint`가 TypeScript 7.1 API를 공식 지원하면 별칭을 제거하고 `typescript@7`로 단일화할 예정입니다.

또한 TypeScript 6부터 `tsconfig.json`의 `types` 기본값이 빈 배열(`[]`)로 변경되었기 때문에, 전역 타입 선언이 필요한 `@types` 패키지(`node`, `react-syntax-highlighter`)는 `tsconfig.json`의 `types` 항목에 명시적으로 등록해 사용합니다.

### 환경 변수

로컬 실행 시 `.env.sample`을 `.env.local`로 복사한 뒤 값을 채워 사용합니다.

| 환경 변수                                    | 설명                                                                                                                                                                   |
| -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_API_BASE_URL`                   | Next.js 서버가 호출하는 백엔드 주소입니다. 쿠버네티스 배포 환경에서는 클러스터 내부 서비스 주소를 사용하며, 비워 두면 `http://localhost:8080`이 기본값으로 적용됩니다. |
| `NEXT_PUBLIC_EXTERNAL_API_BASE_URL`          | 브라우저에서 직접 이동하는 백엔드 공개 주소로, `/api` 경로까지 포함합니다(로컬 예: `http://localhost:8080/api`). Google OAuth 로그인 및 계정 연동 시작 시 사용됩니다.  |
| `NEXT_PUBLIC_RECAPTCHA_V3_SITE_KEY`          | Google reCAPTCHA v3 사이트 키입니다.                                                                                                                                   |
| `NEXT_PUBLIC_RECAPTCHA_V2_CHECKBOX_SITE_KEY` | Google reCAPTCHA v2 체크박스 사이트 키입니다. 로그인 등 인증 과정에서 추가 검증(`403`)이 요구될 때 사용됩니다.                                                         |
| `NEXT_PUBLIC_CDN_BASE_URL`                   | 이미지·영상을 제공하는 Cloudflare R2 공개 도메인 주소입니다. `next/image`의 허용 호스트 설정에도 사용됩니다.                                                           |

## 컨테이너 이미지 및 배포

`Dockerfile`은 의존성 설치, 빌드, 런타임 실행 단계를 분리한 멀티 스테이지 빌드로 구성되어 있습니다. Next.js의 `standalone` 빌드 결과물만 이미지에 포함하며, 보안을 위해 non-root 사용자로 `node server.js`(포트 `3000`)를 실행합니다.

```bash
docker build -t contentria/blog-frontend:<태그> .
```

운영 환경 배포는 `infra/gitops/03-services/blog-frontend-web` 매니페스트를 통해 ArgoCD가 동기화합니다.
