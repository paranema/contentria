// media 컨텍스트의 API 요청 및 응답 타입을 정의합니다. (docs/contexts/media/02-implementation.md)

/** 파일 업로드 전 브라우저가 선언하는 메타데이터이며, 스토리지는 선언된 형식과 크기만 허용합니다. */
export interface PrepareUploadRequest {
  contentType: string;
  sizeBytes: number;
  fileName: string;
}

/** 이미지 업로드 대상 URL과 완료 후 부여될 공개 URL 정보입니다. (흐름 1) */
export interface PreparedImageUpload {
  mediaId: string;
  uploadUrl: string;
  imageUrl: string;
  expiresAt: string;
}

/** 스토리지 파일 검증 및 EXIF 메타데이터 제거가 완료된 이미지 정보입니다. (흐름 2) */
export interface ImageView {
  mediaId: string;
  imageUrl: string;
  contentType: string;
  sizeBytes: number;
  fileName: string;
}

/** 원본 동영상 업로드 대상 URL 정보이며, 업로드 후 워커가 트랜스코딩을 수행합니다. (흐름 V1) */
export interface PreparedVideoUpload {
  videoId: string;
  uploadUrl: string;
  expiresAt: string;
}

export type VideoState = 'TRANSCODING' | 'COMPLETED' | 'FAILED';

/** 게시글 상세 화면용 동영상 정보이며, COMPLETED 상태일 때만 재생 URL이 제공됩니다. (흐름 V4) */
export interface PostVideo {
  videoId: string;
  state: VideoState;
  masterUrl: string | null;
  posterUrl: string | null;
  durationMs: number | null;
  width: number | null;
  height: number | null;
}
