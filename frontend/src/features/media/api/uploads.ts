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

/** 흐름 1: an upload address for one image, valid for a few minutes. */
export async function prepareImageUploadAction(
  request: PrepareUploadRequest
): Promise<ActionResult<PreparedImageUpload>> {
  return attempt(() => apiServer.post<PreparedImageUpload>('/api/media/uploads', request));
}

/** 흐름 2: the API checks the stored file is the image it claimed to be, and makes it usable. */
export async function completeImageUploadAction(mediaId: string): Promise<ActionResult<ImageView>> {
  return attempt(() =>
    apiServer.post<ImageView>(`/api/media/${encodeURIComponent(mediaId)}/complete`, {})
  );
}

/** 흐름 V1: an upload address for a post's video. */
export async function prepareVideoUploadAction(
  request: PrepareUploadRequest
): Promise<ActionResult<PreparedVideoUpload>> {
  return attempt(() => apiServer.post<PreparedVideoUpload>('/api/media/videos/uploads', request));
}
