# 미디어 처리

대용량 이미지와 영상 파일의 업로드 흐름과 게시글 내 적응형 영상 재생 구조를 다룹니다. 서버 측 저장소 검증 및 인코딩 파이프라인은 [backend/docs/contexts/media/](../../../backend/docs/contexts/media/README.md)에 정리되어 있습니다.

## 설계 배경

미디어 원본 바이트가 Next.js 서버(Server Action)나 백엔드 API 서버를 거치면 서버 메모리와 네트워크 대역폭이 급격히 소모되고 업로드 크기 제한에 걸리기 쉽습니다. 따라서 서버는 메타데이터 검증과 서명된 업로드 주소(Presigned URL) 발급만 담당하고, 실제 파일 전송은 브라우저에서 Cloudflare R2로 직접 수행합니다.

## 이미지 업로드

에디터 본문 이미지 업로드는 `features/media/upload/uploadImage.ts`의 `uploadImageToR2`가 3단계로 처리합니다.

1. **브라우저 사전 검증 및 압축**: 허용 형식(`JPEG`, `PNG`, `WebP`, `GIF`), 최대 크기(`10MB`), 파일 헤더 매직 넘버(확장자 위변조 여부)를 먼저 검사합니다. 애니메이션 보존이 필요한 `GIF`를 제외한 이미지는 Web Worker에서 최대 `1MB`·`1920px` 기준으로 압축합니다.
2. **Presigned URL 발급 및 직접 전송**: 압축된 파일의 형식과 크기로 `prepareImageUploadAction`을 호출해 업로드 URL을 발급받은 뒤, 브라우저 `fetch`로 R2에 직접 `PUT` 업로드합니다.
3. **완료 확정 및 본문 삽입**: `completeImageUploadAction`을 호출해 백엔드가 실제 저장된 파일 형식을 재검증하고 위치 정보(EXIF) 등 민감 메타데이터를 제거하도록 요청합니다. 검증이 끝난 공개 이미지 URL만 마크다운 본문에 삽입됩니다.

## 영상 업로드

게시글 첨부 영상 업로드는 `features/media/upload/uploadVideo.ts`의 `uploadVideoToR2`가 처리합니다.

1. **사전 검증 및 URL 발급**: 허용 형식(`MP4`, `MOV`, `WebM`, `MKV`)과 최대 크기(`500MB`)를 확인한 뒤 `prepareVideoUploadAction`으로 Presigned URL과 `videoId`를 발급받습니다.
2. **진행률 기반 업로드**: 브라우저 `fetch` API는 업로드 진행률 이벤트를 제공하지 않으므로 `XMLHttpRequest`(`xhr.upload.onprogress`)를 사용해 R2로 직접 `PUT` 전송하며 화면에 진행률을 표시합니다.
3. **비동기 변환 트리거**: R2 업로드가 완료되면 스토리지 이벤트가 Cloudflare Queues를 통해 백엔드 워커(`apps/worker`)로 전달되어 HLS 변환이 시작됩니다. 프론트엔드는 발급받은 `videoId`를 게시글 저장 요청에 함께 전달해 글과 영상을 연결합니다.

## 영상 재생

게시글 상세 화면의 `VideoPlayer`(`features/media/components/VideoPlayer.tsx`)는 백엔드의 변환 상태(`video.state`)와 브라우저 환경에 따라 다르게 동작합니다.

- **상태별 표시**: 변환이 진행 중이면 안내 문구와 로딩 표시를 렌더링하고, `FAILED` 상태이면 오류 안내를 표시합니다. `COMPLETED` 상태이면서 마스터 플레이리스트(`masterUrl`)가 준비된 경우에만 `<video>` 플레이어를 렌더링합니다.
- **브라우저별 HLS 재생**: Safari와 iOS처럼 네이티브 HLS(`application/vnd.apple.mpegurl`)를 지원하는 환경에서는 `<video src>`에 마스터 URL을 직접 연결합니다. 그 외 브라우저(Chrome, Firefox, Edge)에서는 `hls.js`가 Media Source Extensions(MSE)를 통해 세그먼트를 공급합니다.
- **화질 선택**: `hls.js` 경로에서는 네트워크 상태에 맞춰 화질을 자동 조절하는 적응형 스트리밍(ABR, 기본값 `자동`)을 사용하며, 매니페스트에 복수 해상도(예: `720p`, `480p`)가 있으면 사용자가 수동으로 화질을 고정할 수 있는 선택 UI를 노출합니다.
