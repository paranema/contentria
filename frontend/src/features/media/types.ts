// The media context's API, as backend/contexts/media answers it (docs/contexts/media/02-implementation.md).

/** What the browser declares before putting a file; the storage takes only that type and size. */
export interface PrepareUploadRequest {
  contentType: string;
  sizeBytes: number;
  fileName: string;
}

/** 흐름 1: where to put the image, and the address it will have once completed. */
export interface PreparedImageUpload {
  mediaId: string;
  uploadUrl: string;
  imageUrl: string;
  expiresAt: string;
}

/** 흐름 2: the image after the storage's copy was checked and its photo details removed. */
export interface ImageView {
  mediaId: string;
  imageUrl: string;
  contentType: string;
  sizeBytes: number;
  fileName: string;
}

/** 흐름 V1: where to put the original; the worker transcodes it once it is there. */
export interface PreparedVideoUpload {
  videoId: string;
  uploadUrl: string;
  expiresAt: string;
}

export type VideoState = 'TRANSCODING' | 'COMPLETED' | 'FAILED';

/** A post's video for its page (흐름 V4). Only a completed one has something to play. */
export interface PostVideo {
  videoId: string;
  state: VideoState;
  masterUrl: string | null;
  posterUrl: string | null;
  durationMs: number | null;
  width: number | null;
  height: number | null;
}
