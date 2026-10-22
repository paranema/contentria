'use server';

import apiServer from '@/shared/api/apiServer';
import { PresignedUrlRequest, PresignedUrlResponse } from '@/types/api/media';

export async function requestPresignedUrlAction(
  payload: PresignedUrlRequest
): Promise<PresignedUrlResponse> {
  return await apiServer.post<PresignedUrlResponse>('/api/media/presigned-url', payload, {
    requireAuth: true,
  });
}
