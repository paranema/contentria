# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.
규칙이 바뀌면 01을, 구현 방법이 바뀌면 이 문서를 고친다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 헥사고날 규칙을 따른다.
불변식과 정책을 가르는 기준은 다른 컨텍스트와 같다. 애그리거트 하나만 보고 판단할 수 있으면 도메인, 다른 애그리거트나 저장소를 봐야 하면 앱이다.

## 애그리거트 경계

| 애그리거트 | 루트 | 멤버 | 식별자 |
|---|---|---|---|
| Media | `Media` | 연결된 글 ID 목록 (`media_attachments`) | 이미지 ID |
| Video | `Video` | 연결된 글 ID 목록 (`video_attachments`) | 영상 ID |

**연결은 이미지의 멤버다.** "완료된 이미지만, 올린 회원의 글에만 연결한다"와 "연결된 이미지는 지울 수 없다"는 이미지 하나만 보고 판단한다.
연결을 글 쪽에 두지 않는다. 글은 출판의 애그리거트이고, 미디어는 글 ID만 안다.

| 상태 | 뜻 | 바뀌는 때 |
|---|---|---|
| `UPLOADING` | 업로드 주소를 내줬다 | 흐름 1 |
| `READY` | 검증을 마쳤다. 연결할 수 있다 | 흐름 2 |

검증에 실패한 이미지는 상태를 두지 않고 기록째 지운다. 지운 이미지도 상태를 두지 않는다.
`unattached_since`는 READY인 이미지가 어떤 글에도 연결되지 않게 된 시각이다. 완료할 때 정해지고, 연결되면 비고, 마지막 연결이 풀리면 다시 정해진다. 정리는 이 열만 본다.

## 저장소의 주소

| 항목 | 값 |
|---|---|
| 키 | `images/{이미지 ID}.{확장자}`. 확장자는 선언한 형식에서 정한다(`jpg`, `png`, `webp`, `gif`). 파일 이름은 쓰지 않는다 |
| 이미지 주소 | `{공개 주소}/images/{이미지 ID}.{확장자}`. 공개 주소는 설정(`contentria.media.public-base-url`)이다 |
| 원래 파일 이름 | 기록에만 남긴다. 화면이 보여줄 때 쓴다 |

출판이 넘긴 주소가 미디어의 것인지는 공개 주소로 시작하는지와 키의 형태로 판단한다. 이 판단은 미디어 안에 있다. 출판은 공개 주소를 모른다.

## 규칙의 위치

| 01의 규칙 | 위치 | 비고 |
|---|---|---|
| 흐름 1의 1, 2번 | 도메인(`ImageFormat`, `Media.prepare`) | 형식과 크기는 값만 보고 판단한다 |
| 흐름 1의 3번 | 앱 | 오늘 준비한 양을 저장소에서 더한다 |
| 흐름 2의 2, 6번 | 도메인(`Media.complete`) | 이미 READY면 그대로 |
| 흐름 2의 3번 | 앱 + 저장소 포트 | 파일이 있는지 저장소에 묻는다 |
| 흐름 2의 4번 | 도메인(`ImageFormat.matches(header)`) + 앱 | 시그니처는 순수한 바이트 비교다. 지우는 것은 앱 |
| 흐름 2의 5번 | 앱 + 이미지 처리 포트 | EXIF 제거는 JDK ImageIO를 쓰는 어댑터다 |
| 흐름 3의 1번 | 앱 | 주소를 이미지 ID로 바꾸는 것은 미디어의 주소 규칙이다 |
| 흐름 3의 2~4번 | 도메인(`Media.attachTo(postId, ownerId)`) | |
| 흐름 3의 연결 풀기, 흐름 4 | 도메인(`Media.detachFrom(postId, at)`) | 마지막 연결이 풀리면 `unattached_since`를 정한다 |
| 흐름 5의 2번 | 도메인(`Media.requireDeletable()`) | |
| 흐름 6 | 앱 + 배치 진입점 | 파일을 먼저 지우고 기록을 지운다. 파일이 실패하면 기록을 남긴다 |

| 도메인 | 하는 일 |
|---|---|
| `ImageFormat` | 네 형식의 선언(`image/jpeg` 등), 확장자, 시그니처. `of(contentType)`는 허용하지 않은 형식에 null, `matches(header)`는 파일 앞 12바이트로 판단한다. WebP는 앞의 `RIFF`와 8바이트 뒤의 `WEBP`를 함께 본다 |
| `Media.prepare(uploaderId, contentType, sizeBytes, fileName)` | 형식, 크기(1바이트~10MB), 파일 이름(1~255자). 키는 `images/{ID}.{확장자}` |
| `Media.complete(at)` | 두 번째 부름은 false를 돌려주고 아무것도 바꾸지 않는다 |
| `Media.attachTo(postId, postOwnerId)` | 연결되었는지를 돌려준다. 업로드 중이거나 남의 글이면 false |
| `Media.detachFrom(postId, at)`, `requireDeletable()` | 마지막 연결이 풀릴 때만 `unattached_since`를 정한다 |

연결된 글 목록은 `@ElementCollection`으로 `media_attachments`에 매핑한다. 글 ID 값만 담는 멤버라 엔티티로 두지 않는다.

## 포트와 어댑터

| 포트 | 방향 | 어댑터 | 내용 |
|---|---|---|---|
| `MediaRepository` | 필요 | Spring Data | `application/required`에 둔다 |
| `ObjectStorage` | 필요 | `adapter/required/storage/S3ObjectStorage` | 업로드 주소 발급, 있는지, 읽기, 쓰기, 지우기. AWS SDK의 S3 클라이언트로 R2를 쓴다 |
| `ImageCleaner` | 필요 | `adapter/required/image/ImageIoCleaner` | JPEG를 다시 저장해 사진 정보를 지운다 |
| 업로드 HTTP | 제공 | `adapter/provided/web` | 흐름 1, 2, 5 |
| 글의 이미지 연결 | 제공 | `media-api`의 포트를 `application`이 구현 | 흐름 3, 4. 출판이 부른다 |
| 정리 | 제공 | `adapter/provided/batch` | 흐름 6. `apps/batch`가 띄운다 |

저장소 어댑터는 S3 호환 API만 쓴다. 테스트는 MinIO 컨테이너로 같은 어댑터를 검증한다.

## HTTP

| 요청 | 응답 | 01 |
|---|---|---|
| `POST /media/uploads` `{contentType, sizeBytes, fileName}` | 201. 이미지 ID, 업로드 주소, 이미지 주소, 업로드 주소의 만료 시각 | 흐름 1 |
| `PUT {업로드 주소}` (브라우저가 저장소로) | 저장소의 응답. 선언한 형식(`Content-Type`)과 크기가 아니면 저장소가 거부한다 | 흐름 1 |
| `POST /media/{id}/complete` | 200과 이미지(주소, 형식, 크기, 파일 이름). 이미 완료했어도 200 | 흐름 2 |
| `DELETE /media/{id}` | 204 | 흐름 5 |

모두 로그인이 필요하다.

| 실패 | 응답 |
|---|---|
| 허용하지 않은 형식 | 400 `UNSUPPORTED_IMAGE_FORMAT` |
| 10MB를 넘거나 0바이트 | 400 `IMAGE_TOO_LARGE` |
| 파일 이름이 비었거나 255자를 넘음 | 400 `INVALID_FILE_NAME` |
| 오늘 100MB를 넘음 | 429 `DAILY_UPLOAD_LIMIT` |
| 내 이미지가 아니거나 없음 | 404 `IMAGE_NOT_FOUND` |
| 저장소에 아직 파일이 없음 | 409 `UPLOAD_NOT_FINISHED` |
| 파일의 크기나 시그니처가 선언과 다름, JPEG를 읽을 수 없음 | 400 `IMAGE_CONTENT_MISMATCH`. 기록과 파일을 지운다 |
| 글에 연결된 이미지를 지우려 함 | 409 `IMAGE_IN_USE` |

| 구현에서 정한 것 | 내용 |
|---|---|
| 저장소 호출과 트랜잭션 | 저장소는 트랜잭션 밖에서 부른다. 완료는 파일을 읽고 검증한 뒤 짧은 트랜잭션에서 상태만 바꾼다 |
| 지우는 순서 | 파일을 먼저, 기록을 나중에 지운다. 기록이 먼저 사라지면 남은 파일을 찾을 길이 없다 |
| 하루 양의 동시성 | 같은 순간에 준비한 두 업로드가 함께 한도를 넘을 수 있다. 한도는 남용을 막는 장치이고 장부가 아니다 |
| JPEG를 읽지 못함 | JDK ImageIO가 읽지 못하는 JPEG(CMYK 등)는 형식이 다른 파일처럼 거부한다 |
| 설정 | `contentria.media.public-base-url`, `upload-url-ttl`(10분), `storage.{endpoint, bucket, region, access-key-id, secret-access-key, path-style}`. R2의 region은 `auto` |
| 테스트 저장소 | MinIO 컨테이너. MinIO가 Docker Hub에 이미지를 더 올리지 않아 Chainguard의 `cgr.dev/chainguard/minio:latest`를 쓴다. 무료 공개본은 `latest`만 있어 버전을 고정하지 못한다 |

## 다른 컨텍스트와의 관계

미디어는 다른 컨텍스트를 호출하지 않는다. 출판이 미디어를 호출한다([strategic/04-context-map.md](../../strategic/04-context-map.md)의 "출판과 미디어의 방향").

| `media-api`의 포트 | 출판이 부르는 때 | 하는 일 |
|---|---|---|
| `syncPostImages(postId, ownerId, imageUrls)` | 글을 저장한 트랜잭션이 커밋된 뒤 | 흐름 3. 몇 번을 불러도 결과가 같다 |
| `releasePostImages(postId)` | 글을 지운 트랜잭션이 커밋된 뒤 | 흐름 4 |

쓰기지만 동기 호출이다. [architecture/01-module-structure.md](../../architecture/01-module-structure.md)의 예외("출판이 미디어를 호출해 첨부를 연결하는 것")에 해당한다.

출판 쪽에서 하는 일:

| 일 | 위치 |
|---|---|
| 본문에서 이미지 주소 찾기 | 출판의 마크다운 어댑터(commonmark). 이미지 노드의 주소만 모은다 |
| 글당 이미지 20개 | 출판의 앱 규칙. 저장 전에 본문으로 센다. 넘으면 저장하지 않는다 |
| 연결 호출 | 출판의 `adapter/required/media`(번역 계층). 실패하면 기록만 남기고 저장 응답은 성공이다 |

연결 호출이 실패하면 01의 "글을 저장한 뒤에 연결한다"대로 다음 저장이 다시 맞춘다. 출판은 실패를 되돌리지 않는다.

| 구현 | 위치 |
|---|---|
| `media-api`의 포트 | `PostImageAttachments`(`syncPostImages`, `releasePostImages`). `application/service/PostImageAttachmentService`가 구현한다 |
| 주소를 이미지로 | `ImageAddresses.mediaIdOf(url)`. 공개 주소 + `images/{UUID}.{확장자}`가 아니면 null |
| 이 글의 이미지 | named query `Media.findAttachedTo`(`?1 member of m.postIds`) |
| 출판의 이미지 찾기 | `adapter/required/markdown/CommonmarkImages`. commonmark의 `Image` 노드만 모은다. 코드 안의 이미지 문법과 HTML `<img>`는 세지 않는다 |
| 출판의 규칙과 호출 | `application/service/PostImageLinks`. 저장 전에 20개를 세고, 저장과 삭제가 커밋된 뒤 부른다. 실패는 기록만 남긴다 |
| 출판의 번역 계층 | `adapter/required/media/MediaPostImages` |

글을 쓰거나 고칠 때만 맞춘다. 발행, 비공개, slug 변경은 본문을 바꾸지 않으므로 부르지 않는다.

## 정리

| 구성 | 위치 |
|---|---|
| 유스케이스 | `application/provided/CleanUpImagesUseCase`, `application/service/ImageCleanupService` |
| 만료 판단 | 도메인 `Media.isExpired(now)`. 업로드 중이면 만든 지 24시간, 사용 가능이면 `unattached_since`에서 7일 |
| 대상 찾기 | named query `Media.findExpiredUploadIds`, `Media.findUnusedIds`. 오래된 것부터 종류마다 500개씩, 한 번 실행에 한 묶음 |
| 지우기 | 이미지마다 트랜잭션 하나. `Media.findLockedById`(`SELECT ... FOR UPDATE`)로 잠그고 만료를 다시 판단한 뒤 파일, 기록 순으로 지운다 |
| 잡 | `adapter/provided/batch/ImageCleanupJob`(`mediaImageCleanup`). 설정은 `config/MediaBatchConfiguration`, 띄우는 곳은 `apps/batch` |

잠근 채 저장소를 부르는 것은 요청 경로의 규칙("저장소는 트랜잭션 밖에서")과 다르다. 잠금이 파일 삭제를 감싸야 그사이에 글이 그 이미지를 연결하지 못한다. 연결하려던 쪽은 잠금이 풀린 뒤 이미지가 없음을 보고 실패하고, 출판은 그 실패를 기록만 남긴다.
파일을 지우지 못한 이미지는 기록을 남기고 다음 실행에 다시 지운다. 그런 이미지가 하나라도 있으면 잡을 실패로 끝내, 일정이 실패를 보이게 한다.

잡은 Spring Batch 메타데이터를 남기지 않는다(기본 `ResourcelessJobRepository`). 매번 처음부터 돌아도 남은 것을 찾으므로 이어서 실행할 필요가 없다. 그래서 `batch` 스키마와 `batch_migrator`는 아직 만들지 않았다.
`apps/batch`는 `batch_app` 롤로 접속하고, 지금은 `media` 스키마의 DML만 가진다. 일정은 `--spring.batch.job.name=mediaImageCleanup`(또는 `BATCH_JOB_NAME`)으로 이 잡을 고른다. 주기는 인프라 작업 때 정한다.

## DB 제약

| 제약 | 보증하는 것 |
|---|---|
| `media(stored_key)` UNIQUE | 한 파일에 기록 하나 |
| CHECK `status IN ('UPLOADING', 'READY')` | 상태는 둘뿐이다 |
| CHECK `status = 'READY' OR unattached_since IS NULL` | 업로드 중에는 정리 기준 시각이 없다 |
| `media_attachments(media_id, post_id)` 기본키 | 한 글에 같은 이미지는 한 번만 연결 |
| `media_attachments.media_id → media` 외래키 | 연결은 이미지의 멤버다. 애그리거트 안의 외래키다 |
| `media_attachments(post_id)` 인덱스 | 글의 이미지 맞추기와 해제 |
| `media(uploader_id, created_at)` 인덱스 | 오늘 준비한 양 |
| `media(status, created_at)`, `media(unattached_since)` 인덱스 | 정리 대상 찾기 |

마이그레이션은 `media-core/src/main/resources/db/migration/media/V1__create_media_tables.sql`이다. `media_migrator` 롤이 스키마를 소유하고, `api_app`은 DML만 가진다.
형식(`ck_media_format`)과 크기(`ck_media_size`, 1바이트~10MB)도 CHECK로 한 번 더 막는다. `media_attachments`는 이미지를 지우면 함께 지워진다(`ON DELETE CASCADE`).

`post_id`는 출판의 글을 가리키지만 외래키를 두지 않는다. 컨텍스트를 넘는 외래키는 없다.
미디어는 이벤트를 발행하지도 소비하지도 않으므로 `outbox_event`와 `inbox_event`가 없다.

## 단계

| 단계 | 내용 | 완료 조건 |
|---|---|---|
| M0 | 이 문서와 01-rules, README | 결정 항목 확정 |
| M1 | 모듈 골격(`media-api`, `media-core`), 헥사고날 ArchUnit 규칙, 도메인(`Media`, `ImageFormat`) | 도메인 규칙마다 테스트 |
| M2 | `media` 스키마와 롤, 마이그레이션 | 빈 DB에 적용, 엔티티 매핑 검사 통과 |
| M3 | 업로드 준비와 완료, 지우기 HTTP. 저장소와 이미지 처리 어댑터 | MinIO로 올리고 완료하는 HTTP 테스트. 시그니처가 다른 파일 거부, JPEG의 EXIF 제거 |
| M4 | `media-api`의 연결 포트, 출판의 이미지 찾기와 글당 20개, 저장과 삭제 뒤의 호출 | 글 저장, 수정, 삭제에 따라 연결이 맞춰지는 테스트. 연결이 실패해도 저장은 성공 |
| M5 | 정리(`apps/batch`) | 24시간, 7일 경계와 파일 삭제 실패 시 기록이 남는 테스트 |
| V0 | 영상의 규칙과 구현 배치(01의 "영상", 이 문서의 "영상") | 결정 항목 확정 |
| V1 | `Video` 도메인(상태 기계), 마이그레이션 | 상태 전이마다 테스트, 엔티티 매핑 검사 |
| V2 | 영상 업로드 준비 HTTP, 출판의 첨부 영상(글의 `videoId`)과 저장 뒤 연결, 독자용 조회(`media-api`) | 첨부, 교체, 해제, 남의 영상 거부, 완료 영상만 재생 주소 |
| V3 | `apps/worker`: Cloudflare Queue pull 어댑터, ffmpeg 변환 어댑터, 변환 유스케이스 | ffmpeg 컨테이너로 실제 HLS와 포스터, 영상이 아닌 파일은 실패, 같은 알림 두 번 |
| V4 | 정리(`apps/batch`): 오래 변환 중인 영상, 쓰이지 않는 영상 | 2시간, 7일 경계, 결과 폴더까지 지우기 |

## 영상

| 항목 | 내용 |
|---|---|
| 상태 | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. 전이는 `Video`의 메서드로만 한다(`startTranscoding`, `complete(result)`, `fail(reason)`) |
| 키 | 원본 `raw/{영상 ID}/original.{확장자}`, 변환 결과 `hls/{영상 ID}/`(마스터 `master.m3u8`, 포스터 `poster.jpg`) |
| 연결 | 이미지와 같다. 연결된 글 ID 목록은 `video_attachments`, 정리 기준은 `unattached_since` |
| 원본 이벤트 | R2가 `raw/` 접두사의 생성 이벤트만 큐에 보낸다. `hls/`에 쓴 결과가 다시 변환되지 않게 하는 장치다([operations/cloudflare-video-pipeline.md](../../operations/cloudflare-video-pipeline.md)) |

| 프로세스 | 하는 일 | 진입점 |
|---|---|---|
| `apps/api` | 업로드 준비, 글과의 연결, 독자용 조회 | `adapter/provided/web`, `media-api` |
| `apps/worker` | 큐에서 받아 변환 | `adapter/provided/queue` |
| `apps/batch` | 정리 | `adapter/provided/batch` |

| 포트 | 어댑터 | 내용 |
|---|---|---|
| `VideoQueue` | `adapter/required/queue/CloudflareVideoQueue` | Cloudflare Queues의 `http_pull`. 받기(pull)와 확인(ack). 확인하지 않은 메시지는 다시 온다 |
| `Transcoder` | `adapter/required/transcode/FfmpegTranscoder` | ffprobe로 확인하고 ffmpeg로 HLS와 포스터를 만든다. 영상이 아니거나 너무 길면 "다시 시도하지 않을 실패"로 알린다 |
| `ObjectStorage` | 이미지와 같은 S3 어댑터에 파일 받기, 파일 올리기, 접두사째 지우기를 더한다 | |

| 도메인 메서드 | 하는 일 |
|---|---|
| `Video.prepare(uploaderId, contentType, sizeBytes, fileName, at)` | 형식, 크기(1바이트~500MB), 파일 이름. 원본 키는 `raw/{ID}/original.{확장자}`. 정리 기준 시각을 준비할 때부터 정한다(상태와 상관없이 연결할 수 있으므로) |
| `startTranscoding(at)` | 완료나 실패면 false. 변환 중이면 다시 맡는다(worker가 죽고 큐가 다시 보낸 경우) |
| `complete(result)` | 변환 중일 때만. 아니면 `NotTranscoding` |
| `fail(reason)` | 완료나 실패면 그대로. 이유는 500자까지 |
| `isStuck(now)`, `isExpired(now)` | 변환 시작에서 2시간, 마지막 연결이 풀린 뒤 7일 |

마이그레이션은 `V2__create_video_tables.sql`(`videos`, `video_attachments`)이다. 상태마다 있어야 할 값(변환 중의 시작 시각, 완료의 재생 정보, 실패의 이유)을 CHECK로 막고, 원본 키는 고유하다.

`media-api`에는 둘을 더한다. `PostVideoAttachments.syncPostVideo(postId, ownerId, videoId?)`(흐름 V3), `GetPostVideosQuery.videosOf(postIds)`(흐름 V4, 글 상세 조합용).
출판은 글에 첨부 영상 ID(`videoId`)를 두고, 글을 쓰거나 고칠 때 받는다. 저장이 커밋된 뒤 이미지와 함께 미디어에 알린다.

| 구현 (V2) | 위치 |
|---|---|
| 업로드 준비 | `application/service/VideoUploadService`, `POST /media/videos/uploads`(201: 영상 ID, 업로드 주소, 만료 시각). 실패는 400 `UNSUPPORTED_VIDEO_FORMAT`, `VIDEO_TOO_LARGE` |
| 연결과 조회 | `application/service/PostVideoService`가 `PostVideoAttachments`, `GetPostVideosQuery`를 구현한다. 글이 지워질 때는 `releasePostImages`가 영상까지 풀어, 출판은 한 번만 부른다 |
| 출판 | 글의 `video_id`(마이그레이션 V3). 쓰기와 고치기 요청의 `videoId`. 저장 뒤 `PostImageLinks`가 이미지와 영상을 따로 알린다. 하나가 실패해도 다른 하나는 맞춰진다 |
| 독자용 조합 | `apps/api`의 `GET /posts/{id}/video`. 출판에서 글이 공개인지 확인하고, 미디어에서 영상을 받는다. 공개가 아니거나 영상이 없으면 404 `VIDEO_NOT_FOUND` |

출판은 받은 `videoId`를 그대로 저장한다. 남의 영상이면 미디어가 연결하지 않으므로, 독자에게는 영상이 없는 글로 보인다.

`apps/worker`는 `worker_app` 롤로 접속하고 `media` 스키마의 DML만 가진다. ffmpeg와 ffprobe는 worker의 컨테이너 이미지에 들어 있다.

| 구현 (V3) | 위치 |
|---|---|
| 변환 유스케이스 | `application/provided/TranscodeVideoUseCase`를 `application/service/VideoTranscodingService`가 구현한다. 원본 키로 영상을 찾아 "변환 중"으로 바꾸는 트랜잭션, 트랜잭션 밖의 변환, "완료"나 "실패"로 바꾸는 트랜잭션으로 나눈다. 변환이 몇 분 걸려도 커넥션을 잡고 있지 않는다 |
| 결과 | `SKIPPED`(기록 없음, 이미 완료나 실패), `COMPLETED`, `FAILED`(영상이 아님, 5분 초과). 셋 다 알림을 확인한다. 그 밖의 예외는 일시적 실패로 보고 확인하지 않는다 |
| 원본 지우기 | 완료로 바꾼 뒤 지운다. 지우지 못하면 로그만 남긴다. 남은 원본은 R2 수명 규칙(`raw/` 7일)과 정리(V4)가 지운다 |
| 큐 소비 | `adapter/provided/queue/VideoQueuePoller`. `@Scheduled` 고정 지연으로 받는다. `PutObject`, `CompleteMultipartUpload`가 아니거나 `raw/` 밖의 키면 바로 확인한다. 받기가 실패하면 다음 차례에 다시 받는다 |
| 큐 어댑터 | `adapter/required/queue/CloudflareVideoQueue`. JDK `HttpClient`로 `messages/pull`과 `messages/ack`를 부른다. 메시지 본문이 객체로 오든 문자열로 오든 읽는다. 실패 메시지에는 상태 코드만 담고 토큰은 담지 않는다 |
| 변환 어댑터 | `adapter/required/transcode/FfmpegTranscoder`. 원본을 작업 폴더에 받고, ffprobe로 영상 스트림과 길이를 본다. HLS(fMP4 조각, 6초)는 720p(CRF 23, 최대 3000k, 소리 128k)와 480p(CRF 24, 최대 1500k, 소리 96k)를 한 번의 ffmpeg 실행으로 만든다. 원본보다 높은 단은 빼고, 480p보다 작으면 원본 높이 한 단만 만든다. 포스터는 1초(짧으면 절반) 지점의 한 장이다. 끝나면 작업 폴더를 지운다 |
| 프로세스 실행 | `ProcessRunner` 포트 뒤의 `LocalProcessRunner`. 표준 출력과 오류를 따로 읽어 버퍼가 차서 멈추는 일을 막는다. 시간을 넘기면 강제로 끝낸다 |
| 설정 | `MediaQueueConfiguration`이 위 빈을 직접 만든다. `MediaCoreConfiguration`은 이것들을 스캔하지 않아서, api와 batch는 큐 설정과 ffmpeg 없이 뜬다 |

| 설정 (`contentria.media.worker`) | 환경 변수 | 기본값 |
|---|---|---|
| `poll-interval` | `WORKER_POLL_INTERVAL` | `5s` |
| `queue.account-id`, `queue.queue-id`, `queue.api-token` | `CLOUDFLARE_ACCOUNT_ID`, `CF_QUEUE_ID`, `CF_API_TOKEN` | 없음. 토큰은 계정의 Queues 편집 권한만 가진다 |
| `queue.visibility-timeout`, `queue.batch-size` | | `30m`, `1`. 가장 긴 변환보다 길어서 진행 중인 일이 두 번 나가지 않는다 |
| `transcode.ffmpeg-path`, `transcode.ffprobe-path` | `FFMPEG_PATH`, `FFPROBE_PATH` | `ffmpeg`, `ffprobe` |
| `transcode.max-duration`, `transcode.process-timeout`, `transcode.work-dir` | | `5m`, `20m`, JVM 임시 폴더 |

테스트(`apps/worker`)는 PostgreSQL, MinIO, ffmpeg 컨테이너(`jrottenberg/ffmpeg:7.1-ubuntu2404`)로 돈다. 작업 폴더를 ffmpeg 컨테이너에 같은 경로로 마운트하고, `ProcessRunner`를 그 컨테이너 안에서 실행하는 것으로 바꾼다. 큐는 가짜다. 테스트 영상은 ffmpeg의 `testsrc`와 `sine`으로 만든다. 마스터 재생 목록에서 시작해 플레이어가 받을 파일(재생 목록, 초기화 조각, 조각)이 모두 저장소에 있는지 확인한다. Cloudflare 어댑터는 JDK `HttpServer`로 띄운 가짜 API로 확인한다.
