'use server';

import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { cache } from 'react';
import { PostVideo } from '../types';

/**
 * 발행된 게시글에 첨부된 동영상 정보를 조회하며, 동영상이 없으면 null을 반환합니다. (media 01-rules 흐름 V4)
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
