'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import {
  ImageView,
  PreparedImageUpload,
  PreparedVideoUpload,
  PrepareUploadRequest,
} from '../types';

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

/** 단일 이미지 업로드용 서명된 URL을 발급받습니다. (media 01-rules 흐름 1) */
export async function prepareImageUploadAction(
  request: PrepareUploadRequest
): Promise<ActionResult<PreparedImageUpload>> {
  return attempt(() => apiServer.post<PreparedImageUpload>('/api/media/uploads', request));
}

/** 스토리지에 업로드된 이미지의 유효성을 검증하고 사용 가능 상태로 확정합니다. (media 01-rules 흐름 2) */
export async function completeImageUploadAction(mediaId: string): Promise<ActionResult<ImageView>> {
  return attempt(() =>
    apiServer.post<ImageView>(`/api/media/${encodeURIComponent(mediaId)}/complete`, {})
  );
}

/** 게시글 첨부 동영상 업로드용 서명된 URL을 발급받습니다. (media 01-rules 흐름 V1) */
export async function prepareVideoUploadAction(
  request: PrepareUploadRequest
): Promise<ActionResult<PreparedVideoUpload>> {
  return attempt(() => apiServer.post<PreparedVideoUpload>('/api/media/videos/uploads', request));
}
