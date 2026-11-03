# media 컨텍스트 (미디어)

게시글 본문에 삽입되는 이미지와 게시글에 첨부되는 영상의 업로드, 형식 검증, 트랜스코딩(변환), 게시글과의 연결 및 미사용 파일 정리를 담당하는 지원(Supporting) 서브도메인입니다.

| 애그리거트       | 내부 구성 요소        | 공개 범위                                                                   |
| ---------------- | --------------------- | --------------------------------------------------------------------------- |
| `Media` (이미지) | 연결된 게시글 ID 목록 | 이미지 파일은 URL을 아는 누구나 조회 가능, 업로드 기록은 업로더 본인만 조회 |
| `Video` (영상)   | 연결된 게시글 ID 목록 | 변환이 완료된 영상은 누구나 재생 가능, 업로드 기록은 업로더 본인만 조회     |

- **내부 아키텍처: 헥사고날.** 도메인 규칙 자체는 간결하지만 오브젝트 스토리지(Cloudflare R2 / S3), 메시지 큐(Cloudflare Queues), 미디어 처리 도구(ImageIO, FFmpeg) 등 교체 가능한 외부 인프라 의존성이 많아 헥사고날 아키텍처를 적용합니다([architecture/02-context-architecture.md](../../architecture/02-context-architecture.md) 참고).
- **모듈 위치**: `backend/contexts/media/` (`media-api`, `media-core`)
- **실행 프로세스**: 업로드 준비·완료·연결·조회는 `apps/api`에서, 영상 트랜스코딩은 `apps/worker`에서, 미사용 이미지·영상 정리는 `apps/batch`에서 각각 실행하되 모두 `media-core`의 동일한 도메인 모델을 공유합니다.

## 문서 목록

| 문서                                         | 다루는 내용                                                            |
| -------------------------------------------- | ---------------------------------------------------------------------- |
| [01-rules.md](01-rules.md)                   | 이미지·영상 업로드, 검증, 게시글 연결 및 미사용 파일 정리 규칙         |
| [02-implementation.md](02-implementation.md) | 각 도메인 규칙의 계층별 배치, 포트·어댑터 구성 및 프로세스별 구현 상세 |

## 관련 문서

| 문서                                                                                     | 다루는 내용                                                                       |
| ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| [strategic/04-context-map.md](../../strategic/04-context-map.md)                         | 출판 컨텍스트가 미디어를 단방향으로 호출하는 의존 방향                            |
| [architecture/03-context-integration.md](../../architecture/03-context-integration.md)   | 게시글 저장 트랜잭션 커밋 이후 미디어 첨부 연결을 동기화하는 흐름                 |
| [publishing/01-rules.md](../publishing/01-rules.md)                                      | 마크다운 본문 내 이미지 개수 제한 및 본문 이미지 추출 책임                        |
| [design-notes/media-upload-hardening.md](../../design-notes/media-upload-hardening.md)   | 업로드 용량 제한, 파일 시그니처(Magic Bytes) 검증, EXIF 메타데이터 제거 설계 배경 |
| [operations/cloudflare-video-pipeline.md](../../operations/cloudflare-video-pipeline.md) | R2 이벤트 알림, Cloudflare Queues, 재시도·DLQ 및 `raw/` 접두사 파이프라인 구성    |
