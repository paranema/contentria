# 아키텍처 설계

백엔드의 바운디드 컨텍스트 경계를 프론트엔드 코드베이스에 반영하고, Next.js App Router 환경에서 서버와 브라우저의 역할을 나누는 아키텍처 규칙을 기록합니다.

## 문서 목록

파일 번호는 권장 읽기 순서입니다.

| 문서                                               | 다루는 내용                                                                       |
| -------------------------------------------------- | --------------------------------------------------------------------------------- |
| [01-module-structure.md](01-module-structure.md)   | 디렉터리 역할, 기능 모듈(`features/*`) 경계 규칙, 다중 기능의 화면 조합 방식      |
| [02-rendering.md](02-rendering.md)                 | 경로별 렌더링 방식(SSR·CSR), 라우팅 매핑, 공통 데이터 프리페치                    |
| [03-api-communication.md](03-api-communication.md) | TanStack Query·Server Action·`apiServer` 호출 구조, 오류 직렬화, 런타임 설정 주입 |
| [04-authentication.md](04-authentication.md)       | `HttpOnly` 세션 쿠키 중계, `proxy.ts` 토큰 자동 갱신, 외부 인증 연동              |

## 핵심 원칙

- **바운디드 컨텍스트 1:1 모듈화**: 기술 계층(`components/`, `hooks/` 등)이 아니라 백엔드 바운디드 컨텍스트와 동일한 기능 단위(`features/<기능>/`)로 코드를 나눕니다.
- **기능 간 직접 의존 금지**: 한 기능 모듈은 다른 기능 모듈을 직접 import하지 않습니다. 여러 기능의 데이터나 동작을 한 화면에 모으는 조립 역할은 `app/` 계층에서만 수행합니다.
- **서버 경유 API 통신**: 브라우저는 백엔드 REST API를 직접 호출하지 않고 Server Action을 통해서만 통신합니다. 백엔드 주소, 인증 헤더, 오류 변환은 `shared/api/apiServer.ts` 한 곳에서 처리합니다.
- **빌드와 환경 설정 분리**: `NEXT_PUBLIC_*` 빌드 인라이닝을 사용하지 않고, 서버가 요청 시점에 런타임 환경 변수를 읽어 클라이언트에 전달합니다.

## 자동 검증

모듈 간 의존 경계 규칙은 [eslint.config.js](../../eslint.config.js)의 `no-restricted-imports` 규칙으로 정의되어 있으며, `npm run lint` 및 CI 파이프라인에서 자동으로 검증합니다. `src/features/` 하위 디렉터리를 동적으로 읽어 경계를 생성하므로 새 기능 모듈을 추가할 때 별도로 ESLint 설정을 수정할 필요가 없습니다.
