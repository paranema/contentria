'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import { CategoryTreeView, EditCategoryTreeRequest } from '../types';

/** A blog's categories with the tree's version, or null when there is no such blog. Public. */
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
 * 흐름 2: replaces the whole tree. The version is the one the editor loaded; another change in
 * between fails with CATEGORY_TREE_CHANGED rather than being overwritten.
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
