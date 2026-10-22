'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { BlogView, ChangeBlogSettingsRequest, CreateBlogRequest } from '../types';

/** The signed-in member's blogs, oldest first. */
export const getMyBlogs = cache(async () => apiServer.get<BlogView[]>('/api/me/blogs'));

/** Anyone's blog, or null when there is no such blog. */
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

/** 흐름 1: the blog comes with sample categories and drafts. */
export async function createBlogAction(
  request: CreateBlogRequest
): Promise<ActionResult<BlogView>> {
  try {
    return { success: true, data: await apiServer.post<BlogView>('/api/blogs', request) };
  } catch (error) {
    return toActionError(error);
  }
}

/** The slug cannot change; the title and description can. */
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
