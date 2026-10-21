# media 컨텍스트 (미디어)

글 본문에 들어가는 이미지와 글에 첨부하는 영상의 업로드, 검증, 변환, 글과의 연결, 정리를 담당한다. 지원 서브도메인이다.

| 애그리거트 | 멤버 | 공개 범위 |
|---|---|---|
| `Media` (이미지) | 연결된 글 ID 목록 | 파일은 주소를 아는 누구나. 기록은 올린 회원만 |
| `Video` (영상) | 없음 | 다음 묶음에서 정한다 |

**내부 구조: 헥사고날.** 규칙은 작지만 외부 의존(R2, Cloudflare Queue, ffmpeg)이 많고 바뀔 수 있다([architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)).
코드 위치는 `backend/contexts/media/`(`media-api`, `media-core`)다. 지금은 모듈 골격, 아키텍처 규칙, 이미지 도메인이 있다(M1).

**이미지를 먼저 만들고, 영상은 다음 묶음에서 만든다.** 영상은 변환 프로세스(`apps/worker`), ffmpeg, Cloudflare Queue, 정리 배치까지 필요하다. 이 문서들은 영상의 큰 방향만 적고, 영상 묶음을 시작할 때 자세히 채운다.

## 문서 목록

| 파일 | 내용 | 읽는 사람 |
|---|---|---|
| [01-rules.md](01-rules.md) | 업로드, 검증, 글과의 연결, 정리 규칙 | 누구나 |
| [02-implementation.md](02-implementation.md) | 각 규칙을 어느 계층에 두는지, 포트와 어댑터, 단계 | 구현하는 사람 |

## 다른 문서와의 관계

| 문서 | 다루는 것 |
|---|---|
| [strategic/04-context-map.md](../../strategic/04-context-map.md) | 출판이 미디어를 호출하는 방향. 미디어는 다른 컨텍스트를 호출하지 않는다 |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md) | 글 저장과 이미지 연결의 순서 |
| [publishing/01-rules.md](../publishing/01-rules.md) | 본문 형식과 글당 이미지 수. 본문에서 이미지를 찾는 것은 출판이다 |

## 레거시

레거시 구현은 태그 `legacy-final`에 있다. 참고만 하고, 이 폴더의 규칙을 기준으로 새로 만든다.

| 기능 | 레거시 위치 |
|---|---|
| 이미지 업로드 주소, 승격, 검증, EXIF 제거 | `backend/blog-api/.../api/media/` |
| 영상 업로드 주소, 글과의 연결 | `backend/blog-api/.../api/video/` |
| 영상 변환 (Cloudflare Queue pull, ffmpeg HLS) | `backend/blog-worker/` |
| 영상 정리 | `backend/blog-batch/.../jobs/video/VideoGcJobConfig.kt` |

레거시가 왜 그렇게 만들었는지는 두 기록에 있다. 새 구조에서도 근거로 쓴다.

| 문서 | 내용 |
|---|---|
| [design-notes/media-upload-hardening.md](../../design-notes/media-upload-hardening.md) | 요청 수와 용량 제한, 파일 시그니처 검증, EXIF 제거, CDN 응답 헤더 |
| [operations/cloudflare-video-pipeline.md](../../operations/cloudflare-video-pipeline.md) | R2 이벤트, Cloudflare Queue, 재시도와 DLQ, `raw/` 접두사 |

레거시와 달라진 점은 01-rules의 "레거시와 다른 점"에 모았다.
