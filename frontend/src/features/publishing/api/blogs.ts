'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { BlogView, ChangeBlogSettingsRequest, CreateBlogRequest } from '../types';

/** 로그인한 회원의 블로그 목록을 생성순으로 조회합니다. */
export const getMyBlogs = cache(async () => apiServer.get<BlogView[]>('/api/me/blogs'));

/** 슬러그로 공개 블로그를 조회하며, 블로그가 없으면 null을 반환합니다. */
export const getBlog = cache(async (slug: string): Promise<BlogView | null> => {
  try {
    return await apiServer.get<BlogView>(`/api/blogs/${encodeURIComponent(slug)}`, {
      requireAuth: false,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
});

/** 새 블로그를 생성합니다. 샘플 카테고리와 임시 글이 함께 만들어집니다. (흐름 1) */
export async function createBlogAction(
  request: CreateBlogRequest
): Promise<ActionResult<BlogView>> {
  try {
    return { success: true, data: await apiServer.post<BlogView>('/api/blogs', request) };
  } catch (error) {
    return toActionError(error);
  }
}

/** 블로그 제목과 설명을 변경합니다. 슬러그는 변경할 수 없습니다. */
export async function changeBlogSettingsAction(
  slug: string,
  request: ChangeBlogSettingsRequest
): Promise<ActionResult<BlogView>> {
  try {
    const blog = await apiServer.patch<BlogView>(`/api/blogs/${encodeURIComponent(slug)}`, request);
    revalidatePath('/dashboard/settings');
    return { success: true, data: blog };
  } catch (error) {
    return toActionError(error);
  }
}
