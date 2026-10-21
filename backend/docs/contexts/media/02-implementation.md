# 규칙의 구현

[01-rules.md](01-rules.md)의 `계층` 열을 정하는 기준과, 그 열만으로는 담기 어려운 세부를 적는다.
규칙이 바뀌면 01을, 구현 방법이 바뀌면 이 문서를 고친다.

계층 정의는 [architecture/02-internal-architecture.md](../../architecture/02-internal-architecture.md)의 헥사고날 규칙을 따른다.
불변식과 정책을 가르는 기준은 다른 컨텍스트와 같다. 애그리거트 하나만 보고 판단할 수 있으면 도메인, 다른 애그리거트나 저장소를 봐야 하면 앱이다.

## 애그리거트 경계

| 애그리거트 | 루트 | 멤버 | 식별자 |
|---|---|---|---|
| Media | `Media` | 연결된 글 ID 목록 (`media_attachments`) | 이미지 ID |
| Video | `Video` | 영상 묶음에서 정한다 | 영상 ID |

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
| V0~ | 영상 | 영상 묶음을 시작할 때 이 표를 채운다 |
