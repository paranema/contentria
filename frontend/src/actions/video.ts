'use server';

import apiServer from '@/shared/api/apiServer';
import { VideoPresignedUrlRequest, VideoPresignedUrlResponse } from '@/types/api/video';

export async function requestVideoPresignedUrlAction(
  payload: VideoPresignedUrlRequest
): Promise<VideoPresignedUrlResponse> {
  return await apiServer.post<VideoPresignedUrlResponse>('/api/videos/presigned-url', payload, {
    requireAuth: true,
  });
}
