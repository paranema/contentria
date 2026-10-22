export interface VideoPresignedUrlRequest {
  fileName: string;
  contentType: string;
  fileSize: number;
}

export interface VideoPresignedUrlResponse {
  presignedUrl: string;
  videoId: string;
}

export type VideoStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'DELETED';

/** A post's attached video as the legacy API embedded it. Replaced with media's own (F3). */
export interface PostVideo {
  videoId: string;
  status: VideoStatus;
  masterUrl: string | null;
  posterUrl: string | null;
  durationMs: number | null;
  width: number | null;
  height: number | null;
}
