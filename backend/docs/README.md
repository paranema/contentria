# 백엔드 문서

## 읽는 순서

| 순서 | 폴더 | 다루는 것 |
|---|---|---|
| 1 | [strategic/](strategic/README.md) | 무엇을 어떤 경계로 나누는가. 기술과 무관하다 |
| 2 | [architecture/](architecture/README.md) | 그 경계를 코드로 옮기는 규칙 |
| 3 | [contexts/](contexts/README.md) | 컨텍스트별 제품 규칙과 구현 배치 |

## 참고 문서

순서 없이 필요할 때 찾아 읽는다.

| 폴더 | 다루는 것 | 문서 |
|---|---|---|
| [conventions/](conventions/) | 여러 컨텍스트에 걸친 규약 | [logging.md](conventions/logging.md), [security.md](conventions/security.md) |
| [design-notes/](design-notes/) | 특정 기능의 설계와 결정 이유 | [media-upload-hardening.md](design-notes/media-upload-hardening.md), [refresh-token-grace-window.md](design-notes/refresh-token-grace-window.md) |
| [operations/](operations/) | 외부 서비스 설정, 운영 절차 | [cloudflare-video-pipeline.md](operations/cloudflare-video-pipeline.md) |

## 배치 규칙

### 새 문서의 위치

| 질문 | 위치 |
|---|---|
| 아키텍처를 바꿔도 유지되는 도메인 결정인가 | `strategic/` |
| 모듈, 패키지, 계층, 의존 방향에 관한 규칙인가 | `architecture/` |
| 한 컨텍스트에만 해당하는 규칙인가 | `contexts/<컨텍스트>/` |
| 여러 컨텍스트에 걸친 기술 규약인가 | `conventions/` |
| 특정 기능 하나의 설계와 결정 이유인가 | `design-notes/` |
| 외부 서비스 설정이나 운영 절차인가 | `operations/` |

### 번호 접두사

읽는 순서가 있는 폴더에만 쓴다. 앞 문서가 뒤 문서의 전제다.

| 쓴다 | 쓰지 않는다 |
|---|---|
| `strategic/`, `architecture/`, `contexts/<컨텍스트>/` | `conventions/`, `design-notes/`, `operations/` |

### 그 밖의 규칙

- `strategic/` 문서에는 코드 경로, 모듈 이름, 프레임워크 이름을 쓰지 않는다. 예외는 [strategic/05-legacy-mapping.md](strategic/05-legacy-mapping.md)다.
- `docs/contexts/` 아래 폴더 이름은 코드의 컨텍스트 디렉터리 이름과 같게 한다 (`backend/contexts/member/` ↔ `backend/docs/contexts/member/`).
- 컨텍스트 폴더는 `01-rules.md`(제품 규칙, 아키텍처를 모름)와 `02-implementation.md`(규칙의 계층 배치)로 나눈다.
- 번호를 쓰는 폴더에는 README를 두고 문서 목록과 읽는 순서를 적는다.
- 결정의 이유는 해당 문서의 "결정과 이유" 절에 적는다. 기존 결정을 뒤집으면 이전 결정과 뒤집은 이유를 함께 남긴다.
- 문서를 옮기거나 이름을 바꾸면 그 경로를 참조하는 코드 주석도 함께 고친다.
