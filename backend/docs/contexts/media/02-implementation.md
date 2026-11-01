# 미디어 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 각 규칙을 어느 계층과 컴포넌트에 배치해 구현했는지 기록합니다.
제품의 비즈니스 규칙이 변경되면 `01-rules.md`를, 계층 배치나 기술적 구현 방식이 변경되면 이 문서를 수정합니다.

- 내부 계층 구조는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **헥사고날 아키텍처** 규칙을 따릅니다.
- 단일 애그리거트 상태만으로 판단할 수 있으면 도메인(`도메인`), 다른 애그리거트나 외부 스토리지를 조회해야 판단할 수 있으면 응용 계층(`앱`)에서 처리합니다.

## 애그리거트 경계 및 도메인 모델

미디어 컨텍스트는 본문 이미지를 관리하는 `Media`와 첨부 영상을 관리하는 `Video` 두 애그리거트로 구성됩니다.

| 애그리거트 | 애그리거트 루트 | 내부 구성 요소                                | 식별자(PK) |
| ---------- | --------------- | --------------------------------------------- | ---------- |
| `Media`    | `Media`         | 연결된 게시글 ID 목록 (`media_attachments`)   | 이미지 ID  |
| `Video`    | `Video`         | 연결된 게시글 ID 목록 (`video_attachments`)   | 영상 ID    |

- **게시글 연결 목록은 이미지(`Media`)와 영상(`Video`) 애그리거트의 내부 멤버입니다.** "업로드가 완료된 파일만, 그리고 업로더 본인의 게시글에만 연결한다"는 규칙과 "게시글에 연결된 파일은 직접 삭제할 수 없다"는 규칙 모두 해당 미디어 애그리거트 하나만 보고 판단할 수 있습니다. 연결 정보를 게시글(`Post`) 쪽에 두지 않고 미디어 내부의 값 컬렉션(`@ElementCollection`)으로 관리합니다.
- **이미지 상태(`MediaStatus`)**:
  - `UPLOADING`: 업로드 주소를 발급한 초기 상태(흐름 1)
  - `READY`: 파일 존재 및 시그니처 검증을 통과하여 게시글에 연결할 수 있는 상태(흐름 2)
  - 검증에 실패하거나 삭제된 이미지는 별도의 실패·삭제 상태를 두지 않고 레코드를 즉시 물리 삭제합니다.
- **미연결 시작 시각(`unattached_since`)**: `READY` 상태인 이미지가 어떤 게시글에도 연결되어 있지 않게 된 시점의 시각입니다. 업로드 완료 시점에 처음 기록되고, 하나 이상의 게시글에 연결되면 `NULL`로 비워지며, 마지막 게시글 연결이 해제될 때 다시 현재 시각으로 기록됩니다. 미사용 파일 정리 배치는 이 컬럼만 기준으로 대상을 조회합니다.

## 오브젝트 스토리지 키 및 URL 규칙 (이미지)

업로드된 이미지가 오브젝트 스토리지에 저장될 때 사용하는 객체 키(Key)와 외부에 노출되는 공개 URL 형식입니다.

| 항목             | 규칙 및 형식                                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------------------------------- |
| 스토리지 객체 키 | `images/{이미지 ID}.{확장자}` — 확장자는 선언된 형식에서 결정(`jpg`, `png`, `webp`, `gif`)하며 원본 파일명은 경로에 사용하지 않습니다. |
| 공개 이미지 URL  | `{공개 기본 주소}/images/{이미지 ID}.{확장자}` (`contentria.media.public-base-url` 설정값 사용)               |
| 원본 파일 이름   | DB 레코드에만 보관하며 화면에서 파일 정보를 표시할 때만 사용합니다.                                           |

- 출판 컨텍스트가 전달한 이미지 URL이 미디어 컨텍스트가 관리하는 이미지인지 여부는 URL이 `{공개 기본 주소}/images/{UUID}.{확장자}` 패턴과 일치하는지로 판단합니다(`ImageAddresses.mediaIdOf(url)`). 이 주소 해석 규칙은 미디어 컨텍스트 내부에 캡슐화되어 있으며 출판 컨텍스트는 미디어의 URL 구조를 알지 못합니다.

## 이미지 규칙의 계층별 배치 및 도메인 메서드

[01-rules.md](01-rules.md)의 이미지 관련 흐름(흐름 1~6)을 처리하는 계층과 도메인 메서드 매핑입니다.

| `01-rules.md`의 규칙                      | 담당 위치                                         | 구현 방식 및 비고                                                    |
| ----------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------- |
| 흐름 1-1, 2 (형식 및 파일 크기 검증)      | 도메인 (`ImageFormat`, `Media.prepare`)           | 허용된 MIME 타입과 파일 크기(1바이트~10MB), 파일명 길이를 검증합니다.|
| 흐름 1-3 (일일 업로드 100MB 한도 확인)    | 앱                                                | 당일 준비된 업로드 용량 합계를 리포지토리로 조회해 비교합니다.       |
| 흐름 2-2, 6 (업로드 완료 상태 전이)       | 도메인 (`Media.complete(at)`)                     | 이미 `READY` 상태이면 `false`를 반환하고 상태를 바꾸지 않습니다.     |
| 흐름 2-3 (스토리지 파일 존재 확인)        | 앱 + `ObjectStorage` 포트                         | 오브젝트 스토리지에 실제 객체가 업로드되었는지 조회합니다.           |
| 흐름 2-4 (파일 시그니처 검증 및 실패 삭제)| 도메인 (`ImageFormat.matches(header)`) + 앱       | 앞 12바이트 시그니처 비교는 순수 도메인 함수가, 파일·기록 삭제는 앱이 수행합니다(`WebP`는 앞의 `RIFF`와 8바이트 뒤의 `WEBP`를 함께 확인). |
| 흐름 2-5 (`JPEG` EXIF 메타데이터 제거)    | 앱 + `ImageCleaner` 포트                          | JDK `ImageIO`로 픽셀만 다시 인코딩해 EXIF를 제거한 뒤 덮어씁니다.    |
| 흐름 3-1 (URL에서 이미지 ID 추출)         | 앱 (`ImageAddresses.mediaIdOf`)                   | 외부 사이트 이미지 URL은 `null`로 걸러냅니다.                        |
| 흐름 3-2~4 (게시글에 이미지 연결)         | 도메인 (`Media.attachTo(postId, postOwnerId)`)    | `UPLOADING` 상태이거나 업로더와 게시글 작성자가 다르면 `false`를 반환합니다. |
| 흐름 3 연결 해제 및 흐름 4 (글 삭제 해제) | 도메인 (`Media.detachFrom(postId, at)`)           | 마지막 남은 게시글 연결이 해제될 때만 `unattached_since`를 기록합니다.|
| 흐름 5-2 (연결된 이미지 삭제 거부)        | 도메인 (`Media.requireDeletable()`)               | 연결된 게시글이 하나라도 있으면 예외를 던집니다.                     |
| 흐름 6 (미사용·미완료 이미지 정기 정리)   | 앱 (`ImageCleanupService`) + 배치 어댑터          | 스토리지 파일을 먼저 지운 뒤 DB 레코드를 삭제합니다.                 |

## 포트와 어댑터 구성 (이미지)

헥사고날 아키텍처에 따라 이미지 처리에 사용되는 인바운드·아웃바운드 포트와 어댑터 구현체 목록입니다.

| 포트                           | 방향            | 어댑터 구현체                                  | 역할                                                                 |
| ------------------------------ | --------------- | ---------------------------------------------- | -------------------------------------------------------------------- |
| `MediaRepository`              | 아웃바운드(필요)| Spring Data JPA (`application/required`)       | `Media` 애그리거트 영속화 및 조회                                    |
| `ObjectStorage`                | 아웃바운드(필요)| `adapter/required/storage/S3ObjectStorage`     | Presigned URL 발급, 객체 존재 확인, 읽기·쓰기·삭제 (AWS S3 SDK로 Cloudflare R2 연동, 테스트는 MinIO 컨테이너 사용) |
| `ImageCleaner`                 | 아웃바운드(필요)| `adapter/required/image/ImageIoCleaner`        | `JPEG` 이미지 재인코딩을 통한 EXIF 메타데이터 제거                   |
| 이미지 업로드 웹 API           | 인바운드(제공)  | `adapter/provided/web`                         | 흐름 1(준비), 흐름 2(완료), 흐름 5(삭제) HTTP 엔드포인트 제공        |
| `PostImageAttachments` (`-api`)| 인바운드(제공)  | `application/service/PostImageAttachmentService`| 흐름 3(글 저장 시 동기화), 흐름 4(글 삭제 시 해제) 제공              |
| 이미지 정리 배치 잡            | 인바운드(제공)  | `adapter/provided/batch/ImageCleanupJob`       | 흐름 6 정기 정리 배치 (`apps/batch`에서 실행)                        |

## 이미지 업로드 HTTP 엔드포인트 및 오류 코드

브라우저가 이미지를 업로드하고 완료·삭제할 때 호출하는 엔드포인트입니다(모든 요청은 로그인이 필요합니다).

| HTTP 요청                                                 | 응답                                                                                    | 관련 규칙          |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------- | ------------------ |
| `POST /media/uploads` (`{contentType, sizeBytes, fileName}`)| `201 Created` (이미지 ID, 업로드 주소, 공개 이미지 주소, 업로드 주소 만료 시각 반환)     | 흐름 1 업로드 준비 |
| `PUT {업로드 주소}` (브라우저 → 스토리지 직접 업로드)     | 스토리지 응답 (선언한 `Content-Type` 및 `Content-Length`와 다르면 스토리지가 거부)      | 흐름 1 업로드 실행 |
| `POST /media/{id}/complete`                               | `200 OK` 및 이미지 정보 반환 (이미 완료된 이미지여도 `200`)                             | 흐름 2 업로드 완료 |
| `DELETE /media/{id}`                                      | `204 No Content`                                                                        | 흐름 5 이미지 삭제 |

요청 검증에 실패했을 때 반환하는 HTTP 상태 코드와 오류 코드는 다음과 같습니다.

| 실패 조건                                                           | HTTP 상태 코드 및 오류 코드     |
| ------------------------------------------------------------------- | ------------------------------- |
| 허용되지 않은 이미지 형식                                           | `400 UNSUPPORTED_IMAGE_FORMAT`  |
| 파일 크기가 0바이트이거나 10MB를 초과함                             | `400 IMAGE_TOO_LARGE`           |
| 파일 이름이 비어 있거나 255자를 초과함                              | `400 INVALID_FILE_NAME`         |
| 일일 업로드 용량 한도(100MB) 초과                                   | `429 DAILY_UPLOAD_LIMIT`        |
| 본인이 준비한 이미지가 아니거나 존재하지 않음                       | `404 IMAGE_NOT_FOUND`           |
| 스토리지에 아직 파일이 업로드되지 않음                              | `409 UPLOAD_NOT_FINISHED`       |
| 파일 크기·시그니처가 선언과 다르거나 손상된 `JPEG`(CMYK 등) 파일임  | `400 IMAGE_CONTENT_MISMATCH` (파일 및 기록 즉시 삭제) |
| 게시글에 연결되어 있는 이미지를 삭제하려고 함                       | `409 IMAGE_IN_USE`              |

- **스토리지 I/O와 DB 트랜잭션 분리**: 오브젝트 스토리지 호출은 DB 트랜잭션 밖에서 수행합니다. 업로드 완료 처리 시에도 파일을 읽고 검증·재인코딩하는 작업은 트랜잭션 밖에서 마친 뒤, 마지막에 짧은 트랜잭션을 열어 상태만 변경합니다.
- **파일 선삭제 원칙**: 이미지를 삭제할 때는 항상 **스토리지 파일을 먼저 삭제한 뒤 DB 기록을 삭제합니다.** 만약 DB 기록을 먼저 지웠는데 스토리지 삭제가 실패하면 남은 고아 파일을 추적할 방법이 사라지기 때문입니다.

## 출판 컨텍스트와의 연동 구조

미디어 컨텍스트는 다른 컨텍스트를 호출하지 않으며, 출판 컨텍스트가 `media-api`의 포트를 단방향으로 호출합니다([strategic/04-context-map.md](../../strategic/04-context-map.md) 참고). 이는 [architecture/01-module-structure.md](../../architecture/01-module-structure.md)에서 허용한 동기 호출 예외(게시글 저장 후 미디어 첨부 연결)에 해당합니다.

| `media-api` 공개 포트                        | 출판 컨텍스트의 호출 시점              | 동작 내용                                               |
| -------------------------------------------- | -------------------------------------- | ------------------------------------------------------- |
| `syncPostImages(postId, ownerId, imageUrls)` | 게시글 작성·수정 트랜잭션이 커밋된 직후| 흐름 3 수행 (멱등 보장)                                 |
| `releasePostImages(postId)`                  | 게시글 삭제 트랜잭션이 커밋된 직후     | 흐름 4 수행 (해당 게시글에 연결된 이미지·영상 모두 해제)|

- 출판 컨텍스트 내부에서는 `CommonmarkImages`(`adapter/required/markdown`)가 마크다운 AST의 `Image` 노드에서 URL을 추출하고, `PostImageLinks`(`application/service`)가 저장 전에 이미지 20개 이하 제한을 검사한 뒤 트랜잭션 커밋 이후 `MediaPostImages`(`adapter/required/media`)를 통해 미디어를 호출합니다.
- 게시글 발행, 비공개 전환, slug 변경은 본문을 수정하지 않으므로 미디어 동기화를 호출하지 않습니다.

## 데이터베이스 스키마 및 제약 조건

`media` 스키마의 이미지(`media`) 및 영상(`videos`) 관련 테이블 제약 조건과 인덱스 구성입니다.

| 제약 조건 및 인덱스                                       | 보장하는 도메인 규칙 및 용도                                           |
| --------------------------------------------------------- | ---------------------------------------------------------------------- |
| `media(stored_key)` UNIQUE, `videos(source_key)` UNIQUE   | 스토리지 객체 키당 하나의 레코드만 존재                                |
| `media` CHECK (`status IN ('UPLOADING', 'READY')`)        | 허용된 이미지 상태 값 강제                                             |
| `media` CHECK (`status = 'READY' OR unattached_since IS NULL`)| 업로드 중(`UPLOADING`) 상태에서는 미연결 기준 시각을 두지 않음     |
| `media_attachments(media_id, post_id)` PK 및 FK (`ON DELETE CASCADE`) | 동일 게시글 중복 연결 방지 및 애그리거트 내부 참조 무결성 보장|
| `media_attachments(post_id)`, `video_attachments(post_id)` 인덱스 | 특정 게시글에 연결된 이미지·영상 조회 및 일괄 해제             |
| `media(uploader_id, created_at)` 인덱스                   | 회원의 당일 업로드 용량 합계 조회                                      |
| `media(status, created_at)`, `media(unattached_since)` 인덱스 | 정기 정리 배치의 만료 대상 조회                                    |
| `videos` 상태별 필수 컬럼 CHECK 제약                      | `PROCESSING`의 시작 시각, `COMPLETED`의 재생 메타데이터, `FAILED`의 사유 강제|

- `post_id`는 출판 컨텍스트의 게시글을 가리키므로 컨텍스트 간 외래키(FK)를 두지 않습니다.
- 미디어 컨텍스트는 Kafka 도메인 이벤트를 발행하거나 구독하지 않으므로 `outbox_event`, `inbox_event` 테이블을 두지 않습니다.

---

## 영상 파이프라인 및 정리 배치 구현

첨부 영상(`Video`)의 상태 모델, 오브젝트 스토리지 경로, 그리고 이벤트 큐 연동의 핵심 설계 사항입니다.

| 항목            | 구현 상세                                                                                                                          |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| 영상 상태 전이  | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` — `Video` 엔티티 메서드(`startTranscoding`, `complete(result)`, `fail(reason)`)로만 전이합니다. |
| 스토리지 객체 키| 원본은 `raw/{영상 ID}/original.{확장자}`, 변환 결과는 `hls/{영상 ID}/` 하위(`master.m3u8`, `poster.jpg` 등)에 저장합니다.          |
| R2 이벤트 필터  | R2 버킷은 `raw/` 접두사에서 발생한 객체 생성 이벤트만 Cloudflare Queue로 전송하여, 변환 결과(`hls/`) 저장이 재변환을 유발하지 않도록 차단합니다([operations/cloudflare-video-pipeline.md](../../operations/cloudflare-video-pipeline.md) 참고). |

### 실행 프로세스별 역할과 진입점

영상 파이프라인은 작업의 성격(웹 요청 처리, 장시간 FFmpeg 변환, 정기 정리)에 따라 세 개의 독립 프로세스로 나뉘어 실행됩니다.

| 실행 애플리케이션 | 담당 역할                                 | 인바운드 어댑터 진입점                        | DB 접속 롤     |
| ----------------- | ----------------------------------------- | --------------------------------------------- | -------------- |
| `apps/api`        | 영상 업로드 준비, 게시글 연결, 독자용 조회| `adapter/provided/web`, `media-api`           | `api_app`      |
| `apps/worker`     | 큐 메시지 폴링 및 FFmpeg HLS 트랜스코딩   | `adapter/provided/queue/VideoQueuePoller`     | `worker_app`   |
| `apps/batch`      | 이미지·영상 정기 정리 배치 실행           | `adapter/provided/batch/{Image,Video}CleanupJob`| `batch_app`  |

### 영상 도메인 메서드 및 포트·어댑터

영상 업로드, 큐 소비, FFmpeg 트랜스코딩을 담당하는 주요 도메인 메서드와 포트·어댑터 구현체입니다.

| 구성 요소                                                         | 역할 및 구현 상세                                                                                                                              |
| ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `Video.prepare(uploaderId, contentType, sizeBytes, fileName, at)` | 형식(`MP4`, `MOV`, `WebM`, `MKV`)과 크기(1바이트~500MB)를 검증하고 생성 시점부터 `unattached_since`를 설정합니다.                              |
| `Video.startTranscoding(at)`                                      | 이미 `COMPLETED`나 `FAILED`이면 `false`를 반환하고, `PROCESSING`이면 재시작을 허용합니다(워커 비정상 종료 후 큐가 재전달한 경우).              |
| `Video.complete(result)` / `Video.fail(reason)`                   | 변환 완료 메타데이터를 기록하거나 실패 사유(최대 500자)를 기록합니다.                                                                          |
| `VideoQueue` → `CloudflareVideoQueue`                             | JDK `HttpClient`로 Cloudflare Queues HTTP Pull API(`messages/pull`, `messages/ack`)를 호출합니다.                                              |
| `Transcoder` → `FfmpegTranscoder`                                 | `ffprobe`로 영상 스트림과 길이(5분 이하)를 확인한 뒤, 한 번의 `ffmpeg` 실행으로 HLS(fMP4 세그먼트 6초 단위, `720p`·`480p` 중 원본 이하 해상도)와 포스터 이미지를 생성합니다. |
| `ProcessRunner` → `LocalProcessRunner`                            | 외부 프로세스의 표준 출력과 표준 에러를 별도 스레드로 소비해 버퍼 데드락을 방지하며, 제한 시간(`20m`) 초과 시 프로세스를 강제 종료합니다.      |
| `TranscodeVideoUseCase` → `VideoTranscodingService`               | ① 상태를 `PROCESSING`으로 바꾸는 짧은 트랜잭션 → ② 트랜잭션 외부에서의 파일 다운로드·FFmpeg 변환·업로드 → ③ `COMPLETED`/`FAILED`를 기록하는 짧은 트랜잭션으로 분리하여 수 분이 걸리는 변환 동안 DB 커넥션을 점유하지 않습니다. |
| `PostVideoAttachments`, `GetPostVideosQuery` (`media-api`)        | `PostVideoService`가 구현하며, 독자용 영상 조회는 `apps/api`의 `GET /posts/{id}/video`에서 출판 컨텍스트의 게시글 공개 여부를 확인한 뒤 조합해 반환합니다. |

### 변환 워커 설정 (`contentria.media.worker`)

변환 워커(`apps/worker`)가 큐 폴링 주기와 FFmpeg 실행 환경을 제어하기 위해 사용하는 설정 프로퍼티입니다.

| 프로퍼티                                                   | 환경 변수                                          | 기본값                           |
| ---------------------------------------------------------- | -------------------------------------------------- | -------------------------------- |
| `poll-interval`                                            | `WORKER_POLL_INTERVAL`                             | `5s`                             |
| `queue.account-id`, `queue.queue-id`, `queue.api-token`    | `CLOUDFLARE_ACCOUNT_ID`, `CF_QUEUE_ID`, `CF_API_TOKEN`| 없음 (Queues 편집 권한 토큰 사용)|
| `queue.visibility-timeout`, `queue.batch-size`             | -                                                  | `30m`, `1` (최대 변환 시간보다 길게 설정)|
| `transcode.ffmpeg-path`, `transcode.ffprobe-path`          | `FFMPEG_PATH`, `FFPROBE_PATH`                      | `ffmpeg`, `ffprobe`              |
| `transcode.max-duration`, `transcode.process-timeout`, `transcode.work-dir`| -                                  | `5m`, `20m`, JVM 임시 디렉터리   |

- `MediaQueueConfiguration`은 `apps/worker`에서만 로드되므로 `apps/api`와 `apps/batch`는 Cloudflare Queue 설정이나 `ffmpeg` 바이너리 없이 기동됩니다.

### 이미지 및 영상 정기 정리 배치 (`apps/batch`)

미완료 업로드나 어떤 게시글에도 연결되지 않은 파일, 그리고 장시간 멈춘 변환 작업을 정리하는 두 개의 배치 잡 구성입니다.

| 배치 잡 이름         | 구현 클래스 및 서비스                       | 동작 방식                                                                                                                              |
| -------------------- | ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `mediaImageCleanup`  | `ImageCleanupJob` / `ImageCleanupService`   | 만료된 업로드(24시간 경과)와 미연결 이미지(7일 경과)를 오래된 순으로 각 500건씩 조회한 뒤, 건별 트랜잭션에서 `SELECT ... FOR UPDATE`로 잠그고 만료 여부를 재확인한 다음 스토리지 파일 → DB 기록 순으로 삭제합니다. |
| `mediaVideoCleanup`  | `VideoCleanupJob` / `VideoCleanupService`   | ① 2시간 넘게 `PROCESSING`에 머문 영상을 먼저 `FAILED`로 전환한 뒤, ② 7일 넘게 미연결 상태인 영상을 잠그고 원본 파일, `hls/{ID}/` 하위 전체(`deletePrefix`), DB 기록 순으로 삭제합니다(단, 아직 2시간이 안 된 변환 중 영상은 워커가 작업 중일 수 있으므로 제외). |

- 정리 배치에서 개별 행을 잠근 채 스토리지 삭제를 호출하는 이유는, 파일을 지우는 도중에 게시글 저장 요청이 해당 파일을 다시 연결하는 경쟁 상태를 막기 위함입니다.
- 스토리지 삭제에 실패한 항목이 하나라도 있으면 해당 항목의 DB 기록을 남겨 다음 실행에서 재시도하게 하고, 배치 잡 자체는 실패 상태로 종료하여 스케줄러 모니터링에서 감지할 수 있게 합니다.
- 매 실행 시 DB 조건만으로 남은 대상을 처음부터 조회하므로 Spring Batch 메타데이터 테이블 없이 기본 `ResourcelessJobRepository`로 동작합니다.
