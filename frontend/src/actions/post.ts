'use server';

import apiServer from '@/shared/api/apiServer';
import { Page } from '@/types/api/common';
import {
  CreateNewPostRequest,
  CreateNewPostResponse,
  PostDetailResponse,
  PostStatus,
  PostSummary,
  RecentPostResponse,
  UpdatePostRequest,
  UpdatePostResponse,
} from '@/types/api/posts';
import { revalidatePath } from 'next/cache';

interface GetBlogPostsOptions {
  categorySlug?: string | null;
  statuses?: PostStatus[];
  page?: number;
  size?: number;
}

export async function getBlogPostsAction(
  blogSlug: string,
  options: GetBlogPostsOptions = {}
): Promise<Page<PostSummary> | null> {
  const { categorySlug = null, statuses = [], page = 0, size = 10 } = options;

  const query = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });

  if (categorySlug) {
    query.append('category', categorySlug);
  }

  if (statuses && statuses.length > 0) {
    query.append('statuses', statuses.join(','));
  }

  return await apiServer.get<Page<PostSummary>>(
    `/api/blogs/${blogSlug}/posts?${query.toString()}`,
    {
      requireAuth: false,
      next: {
        tags: [`posts-${blogSlug}`, ...statuses.map((status) => `posts-${blogSlug}-${status}`)], // 태그 기반 재검증(On-demand Revalidation)을 위해 유지
        revalidate: 60,
      },
    }
  );
}

export async function getPostDetailAction(
  blogSlug: string,
  postSlug: string
): Promise<PostDetailResponse | null> {
  return await apiServer.get<PostDetailResponse>(`/api/blogs/${blogSlug}/posts/${postSlug}`, {
    requireAuth: false,
  });
}

export async function getPostDetailByIdAction(postId: string): Promise<PostDetailResponse | null> {
  return await apiServer.get<PostDetailResponse>(`/api/posts/${postId}`, {
    requireAuth: false,
  });
}

export async function createNewPostAction(
  payload: CreateNewPostRequest
): Promise<CreateNewPostResponse> {
  const response = await apiServer.post<CreateNewPostResponse>('/api/posts', payload, {
    requireAuth: true,
  });

  revalidatePath('/dashboard/posts');

  return response;
}

export async function updatePostAction(payload: UpdatePostRequest): Promise<UpdatePostResponse> {
  const response = await apiServer.post<UpdatePostResponse>(
    `/api/posts/${payload.postId}`,
    payload,
    {
      requireAuth: true,
    }
  );

  revalidatePath('/dashboard/posts');

  return response;
}

export async function deletePostAction(postId: string): Promise<void> {
  await apiServer.delete(`/api/posts/${postId}`, {
    requireAuth: true,
  });

  revalidatePath('/dashboard/posts');
}

/**
 * 홈 "최근 발행된 글" — 전체 블로그의 최신 공개 글.
 * apiServer는 cookies()를 읽어 라우트를 동적으로 만들기 때문에 쓰지 않는다 —
 * 공개 데이터라 쿠키가 필요 없고, ISR(5분 재검증)로 홈의 정적 렌더링을 유지한다.
 * 실패해도 홈이 죽지 않게 빈 배열로 폴백.
 */
export async function getRecentPostsAction(size = 6): Promise<RecentPostResponse[]> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
  try {
    const response = await fetch(`${baseUrl}/api/posts/recent?size=${size}`, {
      next: { revalidate: 300 },
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return (await response.json()) as RecentPostResponse[];
  } catch (error) {
    console.error('Failed to fetch recent posts:', error);
    return [];
  }
}
