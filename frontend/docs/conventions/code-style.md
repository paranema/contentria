# 코드 스타일

프론트엔드 워크스페이스에 적용된 정적 분석, 포매팅, 컴파일러, 테스트 규약을 다룹니다.

## 도구 구성

| 영역          | 도구                                              | 설정 파일                       |
| ------------- | ------------------------------------------------- | ------------------------------- |
| 정적 분석     | ESLint 9 (Flat Config)                            | `eslint.config.js`              |
| 코드 포매팅   | Prettier 3, `prettier-plugin-tailwindcss`         | `.prettierrc`                   |
| 에디터 정규화 | EditorConfig                                      | `.editorconfig`                 |
| 타입 검사     | TypeScript 7 (`@typescript/native`), TypeScript 6 | `tsconfig.json`, `package.json` |
| 단위 테스트   | Vitest                                            | `vitest.config.ts`              |

Prettier가 `eslint-plugin-prettier/recommended`를 통해 ESLint에 통합되어 있으므로 포매팅 위반도 `npm run lint` 실행 시 오류로 검출됩니다.

```bash
npm run lint            # ESLint 및 Prettier 규칙 검사
npm run lint -- --fix   # 자동 수정 가능한 항목 반영
npm run typecheck       # TypeScript 7(tsc) 타입 검사
```

## ESLint 규칙

`eslint.config.js`는 다음 규칙 집합을 조합해 검증합니다. 생성물과 외부 자산(`node_modules/`, `.next/`, `out/`, `public/`, 설정 파일)은 검사 대상에서 제외합니다.

- **React**: 자동 JSX 런타임(`jsx-runtime`)을 사용하므로 파일 상단에 `import React from 'react'`를 작성하지 않습니다.
- **React Hooks**: 훅 호출 순서와 의존성 배열(`exhaustive-deps`)을 검증합니다.
- **Next.js**: `recommended`와 `core-web-vitals` 프리셋을 적용해 `<img>` 직접 사용 지양(`next/image` 권장) 및 웹 바이탈 저하 패턴을 검출합니다.
- **TypeScript**: `@typescript-eslint/no-explicit-any`로 무분별한 `any` 사용을 제한합니다. 외부 입력 경계에서는 `unknown`으로 받은 뒤 타입 가드로 좁혀 사용합니다.
- **미사용 식별자**: 사용하지 않는 import와 변수는 오류로 처리합니다. 위치상 생략할 수 없는 인자나 구조 분해 항목은 밑줄 접두사(`_`, `_err` 등)를 붙여 의도를 명시합니다.
- **모듈 경계**: `shared`와 `features/*` 사이의 import 방향을 강제합니다([01-module-structure.md](../architecture/01-module-structure.md) 참고).

## 포매팅 규칙

`.prettierrc`와 `.editorconfig`를 통해 모든 환경에서 동일한 코드 형식을 유지합니다.

| 옵션            | 값     | 설명                                                              |
| --------------- | ------ | ----------------------------------------------------------------- |
| `semi`          | `true` | 문장 끝에 항상 세미콜론을 붙입니다.                               |
| `singleQuote`   | `true` | TypeScript 문자열은 작은따옴표, JSX 속성은 큰따옴표를 사용합니다. |
| `trailingComma` | `es5`  | ES5에서 허용하는 위치에 후행 쉼표를 유지합니다.                   |
| `printWidth`    | `100`  | 한 줄 최대 길이를 100자로 제한합니다.                             |
| `tabWidth`      | `2`    | 공백 2칸 들여쓰기를 사용합니다(`useTabs: false`).                 |
| `endOfLine`     | `lf`   | 운영체제와 무관하게 LF 줄바꿈을 강제합니다.                       |

JSX의 Tailwind 유틸리티 클래스 순서는 `prettier-plugin-tailwindcss`가 공식 권장 순서대로 자동 정렬하므로 수동으로 정렬하지 않습니다.

## TypeScript 구성

TypeScript 7(`@typescript/native`)은 네이티브 컴파일러로 전환되면서 외부 도구가 호출하는 기존 JavaScript API를 아직 제공하지 않습니다. 반면 `typescript-eslint`는 이 API에 의존하므로 `package.json`에 두 버전을 별칭으로 함께 구성합니다.

| 패키지 별칭          | 실제 버전                 | 사용처                                                        |
| -------------------- | ------------------------- | ------------------------------------------------------------- |
| `@typescript/native` | `typescript@7`            | `tsc` CLI 기반 타입 검사 (`npm run typecheck`)                |
| `typescript`         | `@typescript/typescript6` | `typescript-eslint`, IDE 언어 서버, `next build` 시 타입 검사 |

향후 `typescript-eslint`가 TypeScript 7 API를 공식 지원하면 별칭을 제거하고 단일 버전으로 통합할 예정입니다. 또한 TypeScript 6부터 `tsconfig.json`의 `types` 기본값이 빈 배열(`[]`)이므로, 전역 타입 선언이 필요한 `@types` 패키지(`node`, `react-syntax-highlighter`)는 `types` 배열에 명시적으로 등록합니다.

## 단위 테스트

브라우저나 Next.js 런타임 없이 검증할 수 있는 순수 로직(응답 오류 파싱, `Set-Cookie` 파싱, 런타임 환경 변수 해석, 카테고리 트리 변환 등)은 Vitest로 단위 테스트를 작성합니다. `src/**/*.test.ts` 파일이 실행 대상입니다.
