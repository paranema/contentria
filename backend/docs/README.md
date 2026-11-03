# 백엔드 설계 문서

백엔드 아키텍처와 도메인 설계를 정리한 문서 모음입니다.

## 추천 읽기 순서

| 순서 | 디렉터리                                | 다루는 내용                                                     |
| ---- | --------------------------------------- | --------------------------------------------------------------- |
| 1    | [strategic/](strategic/README.md)       | 도메인 경계와 전략적 설계 (특정 기술 스택과 무관한 도메인 결정) |
| 2    | [architecture/](architecture/README.md) | 도메인 경계를 실제 코드와 모듈 구조로 옮기는 아키텍처 규칙      |
| 3    | [contexts/](contexts/README.md)         | 각 바운디드 컨텍스트의 도메인 규칙과 계층별 구현 구조           |

## 참고 문서

아래 문서는 읽기 순서와 관계없이 필요할 때 찾아볼 수 있습니다.

| 디렉터리                       | 다루는 내용                                 | 문서 목록                                                                                                                                        |
| ------------------------------ | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| [conventions/](conventions/)   | 여러 컨텍스트에 공통으로 적용되는 기술 규약 | [logging.md](conventions/logging.md), [security.md](conventions/security.md)                                                                     |
| [design-notes/](design-notes/) | 특정 기능의 상세 설계 및 의사결정 배경      | [media-upload-hardening.md](design-notes/media-upload-hardening.md), [refresh-token-grace-window.md](design-notes/refresh-token-grace-window.md) |
| [operations/](operations/)     | 외부 서비스 연동 설정 및 인프라 운영 절차   | [cloudflare-video-pipeline.md](operations/cloudflare-video-pipeline.md), [container-images.md](operations/container-images.md)                   |

## 문서 작성 및 배치 규칙

### 새 문서의 위치

| 판단 기준                                              | 배치 위치              |
| ------------------------------------------------------ | ---------------------- |
| 기술이나 아키텍처가 바뀌어도 유지되는 도메인 결정인가? | `strategic/`           |
| 모듈, 패키지, 계층, 의존 방향에 관한 규칙인가?         | `architecture/`        |
| 특정 컨텍스트 하나에만 해당하는 규칙인가?              | `contexts/<컨텍스트>/` |
| 여러 컨텍스트에 걸친 공통 기술 규약인가?               | `conventions/`         |
| 특정 기능 하나의 상세 설계와 결정 배경인가?            | `design-notes/`        |
| 외부 서비스 설정이나 운영 절차인가?                    | `operations/`          |

### 파일 번호 접두사

앞 문서가 뒤 문서의 전제가 되어 순서대로 읽어야 하는 디렉터리에만 번호 접두사(`01-`, `02-` 등)를 붙입니다.

| 번호 접두사 사용                                      | 번호 접두사 미사용                             |
| ----------------------------------------------------- | ---------------------------------------------- |
| `strategic/`, `architecture/`, `contexts/<컨텍스트>/` | `conventions/`, `design-notes/`, `operations/` |

### 작성 원칙

- `strategic/` 문서에는 실제 코드 경로, 모듈 이름, 프레임워크 이름을 적지 않습니다.
- `docs/contexts/` 하위 폴더 이름은 실제 코드의 컨텍스트 디렉터리 이름과 일치시킵니다 (`backend/contexts/member/` ↔ `backend/docs/contexts/member/`).
- 각 컨텍스트 폴더는 `01-rules.md`(아키텍처와 무관한 도메인·제품 규칙)와 `02-implementation.md`(규칙을 구현하는 계층 및 컴포넌트 배치)로 구분합니다.
- 번호 접두사를 사용하는 폴더에는 `README.md`를 두어 문서 목록과 읽는 순서를 안내합니다.
- 설계 결정의 배경은 해당 문서의 `설계 배경`(또는 `정책 결정 배경`) 절에 기록합니다. 기존 결정을 변경할 때는 이전 결정과 변경 사유를 함께 남깁니다.
- 문서 경로를 이동하거나 파일명을 변경할 때는 해당 경로를 참조하는 다른 문서와 코드 주석도 함께 수정합니다.
