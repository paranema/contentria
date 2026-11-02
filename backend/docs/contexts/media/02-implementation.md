# 미디어 규칙의 구현

[01-rules.md](01-rules.md)에 정의된 이미지·영상 업로드, 검증·변환, 게시글 연결 동기화 및 미사용 파일 정기 정리 규칙을 실제 코드 계층과 데이터베이스에 어떻게 배치해 구현했는지 다룹니다.

## 아키텍처

### 계층 구조

미디어 컨텍스트(`contexts/media`)는 오브젝트 스토리지(S3/R2), 이미지 재인코딩, Cloudflare Queues, 외부 `ffmpeg` 프로세스 등 다양한 외부 인프라와 연동하므로 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 **헥사고날 아키텍처**를 따릅니다. 단일 애그리거트 상태만으로 판단할 수 있는 규칙은 도메인(`도메인`)에서 처리하고, 다른 애그리거트나 외부 스토리지를 조회해야 판단할 수 있는 규칙은 응용 계층(`앱`)에서 조율합니다.

인바운드·아웃바운드 포트와 어댑터 구현체 구성은 다음과 같습니다.

| 포트                                                    | 방향             | 어댑터 및 구현체                                 | 역할                                                                                                      |
| ------------------------------------------------------- | ---------------- | ------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| `MediaRepository`, `VideoRepository`                    | 아웃바운드(필요) | Spring Data JPA (`application/required`)         | `Media`, `Video` 애그리거트 영속화 및 조회                                                                |
| `ObjectStorage`                                         | 아웃바운드(필요) | `adapter/required/storage/S3ObjectStorage`       | Presigned URL 발급, 객체 존재 확인, 읽기·쓰기·삭제 (AWS S3 SDK로 Cloudflare R2 연동, 테스트는 MinIO 사용) |
| `ImageCleaner`                                          | 아웃바운드(필요) | `adapter/required/image/ImageIoCleaner`          | JDK `ImageIO` 기반 `JPEG` 재인코딩을 통한 EXIF 메타데이터 제거                                            |
| `VideoQueue`                                            | 아웃바운드(필요) | `adapter/required/queue/CloudflareVideoQueue`    | JDK `HttpClient`로 Cloudflare Queues HTTP Pull API(`messages/pull`, `messages/ack`) 호출                  |
| `Transcoder`, `ProcessRunner`                           | 아웃바운드(필요) | `FfmpegTranscoder`, `LocalProcessRunner`         | `ffprobe` 스트림·길이 확인 및 `ffmpeg` HLS·포스터 변환 실행                                               |
| 미디어 업로드 웹 API                                    | 인바운드(제공)   | `adapter/provided/web`                           | 이미지 준비·완료·삭제(흐름 1, 2, 5) 및 영상 준비(흐름 V1) HTTP 엔드포인트 제공                            |
| `PostImageAttachments`, `PostVideoAttachments` (`-api`) | 인바운드(제공)   | `PostImageAttachmentService`, `PostVideoService` | 게시글 저장·삭제 시 이미지·영상 연결 동기화 및 해제(흐름 3, 4, V3)                                        |
| `GetPostVideosQuery` (`-api`)                           | 인바운드(제공)   | `PostVideoService`                               | 독자용 첨부 영상 재생 정보 조회(흐름 V4, `apps/api`의 `GET /posts/{id}/video`에서 호출)                   |
| 영상 변환 큐 폴러                                       | 인바운드(제공)   | `adapter/provided/queue/VideoQueuePoller`        | 큐 메시지 폴링 및 `TranscodeVideoUseCase`(`VideoTranscodingService`) 호출(흐름 V2)                        |
| 이미지·영상 정리 배치 잡                                | 인바운드(제공)   | `adapter/provided/batch/{Image,Video}CleanupJob` | 만료·미연결 이미지 및 영상 정기 정리(흐름 6, V5)                                                          |

### 스프링 설정 구성

웹 요청 처리(`apps/api`), 장시간 FFmpeg 변환(`apps/worker`), 정기 정리 배치(`apps/batch`) 세 프로세스가 각자 필요한 어댑터와 외부 의존성만 로드하도록 스프링 설정을 네 클래스로 분리합니다. 덕분에 `apps/api`와 `apps/batch`는 Cloudflare Queue 설정이나 `ffmpeg` 바이너리 없이 기동됩니다.

| 설정 클래스               | 등록 대상 컴포넌트                                                             | 로드 프로세스                           | DB 접속 롤     |
| ------------------------- | ------------------------------------------------------------------------------ | --------------------------------------- | -------------- |
| `MediaCoreConfiguration`  | 엔티티, 리포지토리, 응용 서비스, 스토리지·이미지 어댑터(`S3ObjectStorage` 등)  | `apps/api`, `apps/worker`, `apps/batch` | 각 프로세스 롤 |
| `MediaWebConfiguration`   | 이미지·영상 업로드 REST 컨트롤러(`adapter/provided/web`)                       | `apps/api`                              | `api_app`      |
| `MediaQueueConfiguration` | `CloudflareVideoQueue`, `FfmpegTranscoder`, `VideoTranscodingService`, 큐 폴러 | `apps/worker`                           | `worker_app`   |
| `MediaBatchConfiguration` | 정기 정리 배치 잡(`ImageCleanupJob`, `VideoCleanupJob`)                        | `apps/batch`                            | `batch_app`    |

## 데이터 모델

### 애그리거트 구성

미디어 컨텍스트는 본문 이미지를 관리하는 `Media`와 첨부 영상을 관리하는 `Video` 두 애그리거트로 구성됩니다. "업로드된 파일만 업로더 본인의 게시글에 연결한다"는 규칙과 "게시글에 연결된 파일은 직접 삭제할 수 없다"는 규칙 모두 미디어 애그리거트 하나만 보고 판단할 수 있으므로, **게시글 연결 목록은 `Post`가 아닌 `Media`·`Video` 애그리거트 내부의 값 컬렉션(`@ElementCollection`)**으로 관리합니다.

| 애그리거트 (`media` 스키마) | 내부 구성 요소                         | 상태 및 미연결 추적 방식                                                                                                                       | 오브젝트 스토리지 키 규칙                                                                           |
| --------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `Media` (`media`)           | 연결된 게시글 ID (`media_attachments`) | `UPLOADING` → `READY` (검증 실패·삭제 시 즉시 물리 삭제). `READY` 진입 시점과 마지막 연결 해제 시점에 `unattached_since`를 기록합니다.         | `images/{이미지 ID}.{확장자}` (공개 URL은 `{public-base-url}/images/{이미지 ID}.{확장자}`)          |
| `Video` (`videos`)          | 연결된 게시글 ID (`video_attachments`) | `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`. 생성 시점부터 미연결이면 `unattached_since`를 기록하고 게시글에 연결되면 `NULL`로 비웁니다. | 원본은 `raw/{영상 ID}/original.{확장자}`, 변환 결과는 `hls/{영상 ID}/`(`master.m3u8`, `poster.jpg`) |

출판 컨텍스트가 전달한 이미지 URL이 미디어 컨텍스트 소속인지 여부는 URL이 `{공개 기본 주소}/images/{UUID}.{확장자}` 패턴과 일치하는지로 판단하며(`ImageAddresses.mediaIdOf(url)`), 원본 파일 이름은 DB 레코드에만 보관합니다. 영상의 경우 R2 버킷에서 `raw/` 접두사의 객체 생성 이벤트만 Cloudflare Queue로 전송하도록 필터링하여, 변환 결과(`hls/`) 저장이 재변환을 유발하지 않도록 차단합니다([operations/cloudflare-video-pipeline.md](../../operations/cloudflare-video-pipeline.md) 참고).

### 데이터베이스 스키마

`post_id`는 출판 컨텍스트의 게시글을 가리키므로 컨텍스트 간 외래키(FK)를 두지 않으며, Kafka 도메인 이벤트를 사용하지 않으므로 `outbox_event`·`inbox_event` 테이블도 두지 않습니다. 데이터 무결성과 정리 배치 조회를 위해 설정한 제약 조건과 인덱스는 다음과 같습니다.

| 테이블                                   | 제약 조건 및 인덱스                                     | 보장하는 도메인 규칙 및 조회 용도                                             |
| ---------------------------------------- | ------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `media`, `videos`                        | `media(stored_key)` UNIQUE, `videos(source_key)` UNIQUE | 스토리지 객체 키당 하나의 레코드만 존재                                       |
| `media`                                  | `CHECK (status IN ('UPLOADING', 'READY'))`              | 허용된 이미지 상태 값 강제                                                    |
| `media`                                  | `CHECK (status = 'READY' OR unattached_since IS NULL)`  | 업로드 중(`UPLOADING`) 상태에서는 미연결 기준 시각을 두지 않음                |
| `videos`                                 | 상태별 필수 컬럼 `CHECK` 제약                           | `PROCESSING`의 시작 시각, `COMPLETED`의 재생 메타데이터, `FAILED`의 사유 강제 |
| `media_attachments`, `video_attachments` | `(media_id, post_id)` PK 및 FK (`ON DELETE CASCADE`)    | 동일 게시글 중복 연결 방지 및 애그리거트 내부 참조 무결성 보장                |
| `media_attachments`, `video_attachments` | `(post_id)` 인덱스                                      | 특정 게시글에 연결된 이미지·영상 조회 및 일괄 해제                            |
| `media`                                  | `(uploader_id, created_at)` 인덱스                      | 회원의 당일 이미지 업로드 용량 합계 조회                                      |
| `media`, `videos`                        | `(status, created_at)`, `(unattached_since)` 인덱스     | 정기 정리 배치의 만료·미연결 대상 조회                                        |

## 비즈니스 흐름 구현

[01-rules.md](01-rules.md)에 정의된 이미지 흐름(흐름 1~6)과 영상 흐름(흐름 V1~V5)의 계층별 구현입니다.

### 흐름 1·2·5. 이미지 업로드·검증·삭제

오브젝트 스토리지 I/O는 DB 트랜잭션 밖에서 수행합니다. 업로드 완료(흐름 2) 처리 시에도 파일을 읽고 시그니처를 검증·재인코딩하는 작업은 트랜잭션 외부에서 마친 뒤, 마지막에 짧은 트랜잭션을 열어 상태만 변경합니다. 또한 이미지를 삭제할 때는 항상 **스토리지 파일을 먼저 삭제한 뒤 DB 기록을 삭제**하여, 스토리지 삭제 실패 시 고아 파일이 추적 불가능해지는 것을 막습니다.

| 규칙 분류                             | 담당 위치                               | 구현 상세                                                                                                                    |
| ------------------------------------- | --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| 형식·크기 검증 및 준비 (흐름 1-1, 2)  | 도메인 (`ImageFormat`, `Media.prepare`) | 허용된 MIME 타입과 파일 크기(1바이트~10MB), 파일명 길이(1~255자)를 검증합니다.                                               |
| 일일 100MB 한도 확인 (흐름 1-3)       | 앱                                      | 당일 준비된 업로드 용량 합계를 리포지토리로 조회해 비교합니다.                                                               |
| 완료 상태 전이 (흐름 2-2, 6)          | 도메인 (`Media.complete(at)`)           | 이미 `READY` 상태이면 `false`를 반환하고 상태를 바꾸지 않습니다.                                                             |
| 시그니처 검증 및 실패 삭제 (흐름 2-4) | 도메인 (`ImageFormat.matches`) + 앱     | 앞 12바이트 시그니처 비교는 도메인 함수가 수행하고(`WebP`는 `RIFF`와 `WEBP` 동시 확인), 실패 시 앱이 파일·기록을 삭제합니다. |
| `JPEG` EXIF 제거 (흐름 2-5)           | 앱 + `ImageCleaner` 포트                | JDK `ImageIO`로 픽셀만 다시 인코딩해 EXIF를 제거한 뒤 동일 키에 덮어씁니다.                                                  |
| 연결된 이미지 삭제 거부 (흐름 5-2)    | 도메인 (`Media.requireDeletable()`)     | 연결된 게시글이 하나라도 있으면 `IMAGE_IN_USE` 예외를 던집니다.                                                              |

### 흐름 V1·V2·V4. 영상 업로드·변환·재생 조회

영상 트랜스코딩은 수 분이 걸릴 수 있으므로 `VideoTranscodingService`는 **① 상태를 `PROCESSING`으로 바꾸는 짧은 트랜잭션 → ② 트랜잭션 외부에서의 원본 다운로드·FFmpeg 변환·HLS 업로드 → ③ `COMPLETED` 또는 `FAILED`를 기록하는 짧은 트랜잭션**으로 분리하여 변환 동안 DB 커넥션을 점유하지 않습니다.

| 구성 요소                                                         | 역할 및 구현 상세                                                                                                                                                                                                                                              |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Video.prepare(uploaderId, contentType, sizeBytes, fileName, at)` | 형식(`MP4`, `MOV`, `WebM`, `MKV`)과 크기(1바이트~500MB)를 검증하고 생성 시점부터 `unattached_since`를 설정합니다.                                                                                                                                              |
| `Video.startTranscoding(at)`                                      | 이미 `COMPLETED`나 `FAILED`이면 `false`를 반환하고, `PROCESSING`이면 재시작을 허용합니다(워커 비정상 종료 후 큐가 재전달한 경우).                                                                                                                              |
| `Video.complete(result)` / `Video.fail(reason)`                   | 변환 완료 메타데이터를 기록하거나 실패 사유(최대 500자)를 기록합니다.                                                                                                                                                                                          |
| `FfmpegTranscoder` / `LocalProcessRunner`                         | `ffprobe`로 영상 스트림과 길이(5분 이하)를 확인한 뒤, 한 번의 `ffmpeg` 실행으로 HLS(fMP4 6초 세그먼트, `720p`·`480p`)와 포스터를 생성합니다. 표준 출력·에러를 별도 스레드로 소비해 버퍼 데드락을 방지하며 제한 시간(`20m`) 초과 시 프로세스를 강제 종료합니다. |
| `GetPostVideosQuery` (`media-api`)                                | 독자용 영상 재생 조회는 `apps/api`의 `PostVideoController`(`GET /posts/{id}/video`)에서 출판 컨텍스트의 게시글 공개 여부를 먼저 확인한 뒤 조합해 반환합니다.                                                                                                   |

변환 워커(`apps/worker`)가 큐 폴링 주기와 FFmpeg 실행 환경을 제어하기 위해 사용하는 설정 프로퍼티(`contentria.media.worker`)는 다음과 같습니다.

| 프로퍼티                                                                    | 환경 변수                                              | 기본값                                    |
| --------------------------------------------------------------------------- | ------------------------------------------------------ | ----------------------------------------- |
| `poll-interval`                                                             | `WORKER_POLL_INTERVAL`                                 | `5s`                                      |
| `queue.account-id`, `queue.queue-id`, `queue.api-token`                     | `CLOUDFLARE_ACCOUNT_ID`, `CF_QUEUE_ID`, `CF_API_TOKEN` | 없음 (Queues 편집 권한 토큰 사용)         |
| `queue.visibility-timeout`, `queue.batch-size`                              | -                                                      | `30m`, `1` (최대 변환 시간보다 길게 설정) |
| `transcode.ffmpeg-path`, `transcode.ffprobe-path`                           | `FFMPEG_PATH`, `FFPROBE_PATH`                          | `ffmpeg`, `ffprobe`                       |
| `transcode.max-duration`, `transcode.process-timeout`, `transcode.work-dir` | -                                                      | `5m`, `20m`, JVM 임시 디렉터리            |

### 흐름 3·4·V3. 게시글 연결 동기화

미디어 컨텍스트는 다른 컨텍스트를 호출하지 않으며, 출판 컨텍스트가 게시글 저장·삭제 트랜잭션을 **커밋한 직후** `media-api`의 포트를 단방향으로 동기 호출합니다([architecture/01-module-structure.md](../../architecture/01-module-structure.md)에서 허용한 동기 호출 예외). 게시글 발행, 비공개 전환, slug 변경은 본문이나 첨부 영상을 수정하지 않으므로 동기화를 호출하지 않습니다.

| `media-api` 공개 포트 및 도메인 메서드                  | 출판 컨텍스트의 호출 시점           | 동작 내용                                                                                       |
| ------------------------------------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------- |
| `syncPostImages(postId, ownerId, imageUrls)`            | 게시글 작성·수정 트랜잭션 커밋 직후 | `ImageAddresses.mediaIdOf`로 ID를 추출해 `Media.attachTo`·`detachFrom` 수행 (멱등 보장)         |
| `syncPostVideo(postId, ownerId, videoId)`               | 게시글 작성·수정 트랜잭션 커밋 직후 | 해당 게시글의 첨부 영상을 `Video.attachTo`·`detachFrom`으로 동기화                              |
| `releasePostImages(postId)`, `releasePostVideo(postId)` | 게시글 삭제 트랜잭션 커밋 직후      | 해당 게시글에 연결된 모든 이미지·영상의 연결을 해제하고 마지막 연결이면 `unattached_since` 기록 |

### 흐름 6·V5. 미사용 파일 정기 정리 배치

배치 서버(`apps/batch`)는 미완료 업로드, 미연결 파일, 장시간 멈춘 변환 작업을 정리하는 두 개의 배치 잡을 실행합니다. 매 실행 시 DB 조건만으로 남은 대상을 처음부터 조회하므로 Spring Batch 메타데이터 테이블 없이 기본 `ResourcelessJobRepository`로 동작합니다.

| 배치 잡 이름        | 구현 클래스 및 서비스                     | 동작 방식                                                                                                                                                                                                                      |
| ------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `mediaImageCleanup` | `ImageCleanupJob` / `ImageCleanupService` | 만료된 업로드(24시간 경과)와 미연결 이미지(7일 경과)를 오래된 순으로 각 500건씩 조회한 뒤, 건별 트랜잭션에서 `SELECT ... FOR UPDATE`로 잠그고 만료 여부를 재확인한 다음 스토리지 파일 → DB 기록 순으로 삭제합니다.             |
| `mediaVideoCleanup` | `VideoCleanupJob` / `VideoCleanupService` | ① 2시간 넘게 `PROCESSING`에 머문 영상을 먼저 `FAILED`로 전환한 뒤, ② 7일 넘게 미연결 상태인 영상을 잠그고 원본 파일, `hls/{ID}/` 하위 전체(`deletePrefix`), DB 기록 순으로 삭제합니다(아직 2시간이 안 된 변환 중 영상은 제외). |

정리 배치에서 개별 행을 잠근 채 스토리지 삭제를 호출하는 이유는, 파일을 지우는 도중에 게시글 저장 요청이 해당 파일을 다시 연결하는 경쟁 상태를 막기 위함입니다. 스토리지 삭제에 실패한 항목이 하나라도 있으면 해당 항목의 DB 기록을 남겨 다음 실행에서 재시도하게 하고, 배치 잡은 실패 상태로 종료하여 스케줄러 모니터링에서 감지할 수 있게 합니다.

### HTTP 엔드포인트

브라우저가 이미지와 영상을 업로드하고 완료·삭제할 때 호출하는 엔드포인트입니다(모든 업로드·관리 요청은 로그인이 필요합니다).

| HTTP 요청                                                    | 응답                                                                       | 대응 흐름                 |
| ------------------------------------------------------------ | -------------------------------------------------------------------------- | ------------------------- |
| `POST /media/uploads` (`{contentType, sizeBytes, fileName}`) | `201 Created` (이미지 ID, 업로드 주소, 공개 이미지 주소, 만료 시각 반환)   | 흐름 1 이미지 업로드 준비 |
| `POST /media/{id}/complete`                                  | `200 OK` 및 이미지 정보 반환 (이미 완료된 이미지여도 `200`)                | 흐름 2 이미지 업로드 완료 |
| `DELETE /media/{id}`                                         | `204 No Content`                                                           | 흐름 5 이미지 직접 삭제   |
| `POST /media/videos` (`{contentType, sizeBytes, fileName}`)  | `201 Created` (영상 ID, 업로드 주소, 만료 시각 반환)                       | 흐름 V1 영상 업로드 준비  |
| `GET /posts/{id}/video` (`apps/api` 조합 컨트롤러)           | 첨부 영상 상태 및 완료 시 HLS 재생 주소·포스터·해상도 반환 (비로그인 허용) | 흐름 V4 독자용 영상 조회  |

요청 검증에 실패했을 때 반환하는 HTTP 상태 코드와 오류 코드는 다음과 같습니다.

| 실패 조건                                                          | HTTP 상태 코드 및 오류 코드                                 |
| ------------------------------------------------------------------ | ----------------------------------------------------------- |
| 허용되지 않은 이미지·영상 형식                                     | `400 UNSUPPORTED_IMAGE_FORMAT` / `UNSUPPORTED_VIDEO_FORMAT` |
| 파일 크기가 0바이트이거나 최대 크기(이미지 10MB, 영상 500MB) 초과  | `400 IMAGE_TOO_LARGE` / `VIDEO_TOO_LARGE`                   |
| 파일 이름이 비어 있거나 255자를 초과함                             | `400 INVALID_FILE_NAME`                                     |
| 이미지 일일 업로드 용량 한도(100MB) 초과                           | `429 DAILY_UPLOAD_LIMIT`                                    |
| 본인이 준비한 미디어가 아니거나 존재하지 않음                      | `404 IMAGE_NOT_FOUND` / `VIDEO_NOT_FOUND`                   |
| 스토리지에 아직 이미지 파일이 업로드되지 않음                      | `409 UPLOAD_NOT_FINISHED`                                   |
| 파일 크기·시그니처가 선언과 다르거나 손상된 `JPEG`(CMYK 등) 파일임 | `400 IMAGE_CONTENT_MISMATCH` (파일 및 기록 즉시 삭제)       |
| 게시글에 연결되어 있는 이미지를 삭제하려고 함                      | `409 IMAGE_IN_USE`                                          |
