# Contentria Frontend

Next.js App Router 기반의 Contentria 웹 프론트엔드입니다. 공개 블로그와 게시글은 검색 엔진 노출(SEO)을 위해 서버에서 렌더링하고, 대시보드와 에디터는 클라이언트에서 동작합니다.

아키텍처와 개발 규약의 전체 목차 및 추천 읽기 순서는 [docs/README.md](docs/README.md)에 정리되어 있습니다.

## 기술 스택

| 분류        | 기술                                              |
| ----------- | ------------------------------------------------- |
| 프레임워크  | Next.js 16 (App Router, Turbopack)                |
| 언어        | TypeScript 7, React 19                            |
| 스타일링    | Tailwind CSS 3                                    |
| UI 컴포넌트 | shadcn/ui (Radix UI)                              |
| 서버 상태   | TanStack Query 5                                  |
| 폼 검증     | React Hook Form, Zod 4                            |
| 에디터      | MDXEditor (CodeMirror)                            |
| 마크다운    | `react-markdown`, `remark-gfm`, `rehype-sanitize` |
| 영상 재생   | `hls.js`                                          |
| 테스트      | Vitest                                            |

## 프로젝트 구조

단방향 계층(`app → features → shared`)과 도메인 단위 모듈 격리를 결합한 **기능 기반 아키텍처(Feature-based Architecture)**를 따르며, 브라우저가 백엔드 REST API를 직접 호출하지 않고 Next.js 서버(Server Component·Server Action)를 경유하는 **BFF(Backend For Frontend)** 패턴으로 동작합니다.

### 기능 모듈

화면 코드는 백엔드의 6개 바운디드 컨텍스트와 1:1로 대응하는 기능 모듈로 분리되어 있습니다.

| 기능 모듈      | 담당 도메인                                                                               |
| -------------- | ----------------------------------------------------------------------------------------- |
| `member`       | 회원가입, 로그인, 세션, 프로필, 계정 설정                                                 |
| `publishing`   | 블로그 개설, 카테고리 관리, 게시글 에디터, 공개 블로그·글 조회                            |
| `interaction`  | 블로그 구독, 게시글 좋아요, 댓글                                                          |
| `notification` | 알림함 조회 및 읽음 처리                                                                  |
| `media`        | 이미지·영상 직접 업로드, HLS 영상 재생 ([설계 노트](docs/design-notes/media-handling.md)) |
| `analytics`    | 방문 기록 수집, 통계 대시보드                                                             |

### 디렉터리 구성

```text
frontend/
├── src/
│   ├── app/        라우팅, 레이아웃, 기능 간 화면 조합
│   ├── features/   도메인별 기능 모듈 (api, types, hooks, components)
│   ├── shared/     공용 API 클라이언트, UI 컴포넌트, 유틸리티
│   ├── types/      환경 변수 타입 선언
│   └── proxy.ts    세션 토큰 갱신, 보호 경로 제어
└── docs/           아키텍처 및 개발 규약 문서
```

모듈 간 의존 규칙과 렌더링·통신·인증 구조는 [docs/architecture/](docs/architecture/README.md)에서 확인할 수 있습니다.

## 개발

### 명령어

```bash
npm ci             # package-lock.json 기준 의존성 클린 설치 (ci: Clean Install)
npm run dev        # 로컬 개발 서버 실행 (http://localhost:3000)
npm run build      # 프로덕션 빌드
npm start          # 프로덕션 서버 실행
npm run lint       # 코드 스타일 및 모듈 경계 규칙 검사
npm run typecheck  # TypeScript 타입 검사
npm test           # 단위 테스트 실행
```

린트·포매팅 규칙과 TypeScript 6·7 병행 구성의 배경은 [docs/conventions/code-style.md](docs/conventions/code-style.md)에 정리되어 있습니다.

### 환경 변수

로컬 실행 시 `.env.sample`을 `.env.local`로 복사해 값을 설정합니다. 운영 환경에서는 컨테이너 기동 시 런타임 환경 변수로 주입합니다([런타임 설정 구조](docs/architecture/03-api-communication.md#런타임-설정)).

| 변수명                  | 설명                                                                                                                |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `INTERNAL_API_BASE_URL` | Next.js 서버가 호출하는 백엔드 내부 주소입니다. 기본값은 `http://localhost:8080`입니다.                             |
| `PUBLIC_API_BASE_URL`   | Google OAuth 등 브라우저가 직접 이동하는 백엔드 공개 주소입니다. 비워 두면 현재 도메인 기준 상대 경로로 이동합니다. |
| `RECAPTCHA_V3_SITE_KEY` | Google reCAPTCHA v3 사이트 키입니다. 로컬 개발 시 비워 둘 수 있습니다.                                              |
| `RECAPTCHA_V2_SITE_KEY` | 추가 검증 시 사용하는 Google reCAPTCHA v2 사이트 키입니다. 로컬 개발 시 비워 둘 수 있습니다.                        |

## 배포

`Dockerfile`은 멀티 스테이지 빌드를 통해 Next.js `standalone` 결과물만 이미지에 포함하며, 보안을 위해 non-root 사용자(`1001:1001`)로 서버(포트 `3000`)를 실행합니다.

`main` 브랜치에 프론트엔드 변경이 반영되면 GitHub Actions(`.github/workflows/frontend-images.yml`)가 정적 검사와 단위 테스트를 수행한 뒤 멀티 아키텍처 이미지를 `ghcr.io/paranema/contentria-frontend`에 게시합니다. 클러스터 배포는 `infra/gitops/03-services/contentria-frontend-web` 매니페스트를 통해 Argo CD가 동기화합니다.
