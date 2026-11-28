'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { CategoryTreeView, EditCategoryTreeRequest } from '../types';

/** 블로그의 카테고리 트리와 버전을 공개 조회하며, 블로그가 없으면 null을 반환합니다. */
export const getCategoryTree = cache(async (blogSlug: string): Promise<CategoryTreeView | null> => {
  try {
    return await apiServer.get<CategoryTreeView>(
      `/api/blogs/${encodeURIComponent(blogSlug)}/categories`,
      {
        requireAuth: false,
      }
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
});

/**
 * 카테고리 트리 전체를 교체합니다. 편집기가 불러온 버전과 다르면 덮어쓰지 않고
 * CATEGORY_TREE_CHANGED 오류로 실패합니다. (흐름 2)
 */
export async function editCategoryTreeAction(
  blogSlug: string,
  request: EditCategoryTreeRequest
): Promise<ActionResult<CategoryTreeView>> {
  try {
    const tree = await apiServer.put<CategoryTreeView>(
      `/api/blogs/${encodeURIComponent(blogSlug)}/categories`,
      request
    );
    revalidatePath('/dashboard/categories');
    return { success: true, data: tree };
  } catch (error) {
    return toActionError(error);
  }
}
