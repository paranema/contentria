'use server';

import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { cache } from 'react';
import { PostVideo } from '../types';

/**
 * A published post's video, or null when it has none to show. `apps/api` puts this together:
 * it answers only for a post readers may see (media 01-rules 흐름 V4).
 */
export const getPostVideo = cache(async (postId: string): Promise<PostVideo | null> => {
  try {
    return await apiServer.get<PostVideo>(`/api/posts/${encodeURIComponent(postId)}/video`, {
      requireAuth: false,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
});
