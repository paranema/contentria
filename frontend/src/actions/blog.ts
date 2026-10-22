'use server';

import apiServer from '@/shared/api/apiServer';
import {
  BlogInfo,
  BlogLayoutResponse,
  CreateBlogPayload,
  CreateBlogResponse,
} from '@/types/api/blogs';

export async function getMyBlogAction(): Promise<BlogInfo[] | null> {
  return await apiServer.get<BlogInfo[]>(`/api/blogs/me`, {
    requireAuth: true,
  });
}

export async function getBlogLayoutAction(slug: string): Promise<BlogLayoutResponse | null> {
  return await apiServer.get<BlogLayoutResponse>(`/api/blogs/layout/${slug}`, {
    requireAuth: false,
    next: {
      revalidate: 3600, // 1시간 캐시
    },
  });
}

export async function createBlogAction(payload: CreateBlogPayload): Promise<CreateBlogResponse> {
  return await apiServer.post<CreateBlogResponse>('/api/blogs/create', payload, {
    requireAuth: true,
  });
}

export interface UpdateBlogSettingsPayload {
  title: string;
  description: string | null;
}

/** 블로그 제목/설명 수정 — 소유자 전용 (slug는 변경 불가) */
export async function updateBlogSettingsAction(
  blogSlug: string,
  payload: UpdateBlogSettingsPayload
): Promise<BlogInfo> {
  return await apiServer.put<BlogInfo>(`/api/blogs/${blogSlug}/settings`, payload, {
    requireAuth: true,
  });
}
