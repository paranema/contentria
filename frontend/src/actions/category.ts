'use server';

import apiServer from '@/shared/api/apiServer';
import { CategoryResponse, SyncCategoryPayload } from '@/types/api/category';
import { getMyBlogAction } from './blog';

export async function getCategoriesAction(): Promise<CategoryResponse[]> {
  try {
    const blog = await getMyBlogAction();
    const blogId = blog?.[0]?.id;
    if (!blogId) {
      return [];
    }

    return await apiServer.get<CategoryResponse[]>(`/api/categories?blogId=${blogId}`, {
      requireAuth: true,
    });
  } catch (error) {
    console.error('Failed to fetch categories:', error);
    return [];
  }
}

export async function syncCategoriesAction(
  blogId: string,
  payload: SyncCategoryPayload[]
): Promise<void> {
  try {
    return await apiServer.post<void>(`/api/categories/sync/${blogId}`, payload, {
      requireAuth: true,
    });
  } catch (error) {
    console.error('Failed to sync categories:', error);
    throw error;
  }
}
