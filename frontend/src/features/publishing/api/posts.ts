'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { getApiBaseUrl } from '@/shared/api/apiBase';
import { ApiError } from '@/shared/api/apiError';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { cache } from 'react';
import {
  MovedPost,
  PostPage,
  PostRequest,
  PostView,
  PublishedPostView,
  PublishedTitle,
} from '../types';

const enc = encodeURIComponent;

// 독자용 공개 조회 API입니다. (흐름 4)

/** 블로그의 발행된 글을 최신순으로 조회합니다. category를 지정하면 해당 카테고리와 하위 카테고리의 글만 조회합니다. */
export const getPublishedPosts = cache(
  async (blogSlug: string, page: number, category?: string | null): Promise<PostPage> => {
    const query = new URLSearchParams({ page: String(page) });
    if (category) query.set('category', category);
    return apiServer.get<PostPage>(`/api/blogs/${enc(blogSlug)}/posts?${query}`, {
      requireAuth: false,
    });
  }
);

/**
 * 홈 화면에 표시할 전체 블로그의 최신 발행 글을 조회합니다.
 * 정적 렌더링(5분 주기 재검증)을 유지하기 위해 쿠키 없이 호출하며, 실패 시 빈 페이지를 반환합니다.
 */
export async function getRecentPosts(): Promise<PostPage> {
  try {
    const response = await fetch(`${getApiBaseUrl()}/api/posts?page=0`, {
      next: { revalidate: 300 },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as PostPage;
  } catch (error) {
    console.error('Failed to fetch recent posts:', error);
    return { items: [], page: 0, totalItems: 0, totalPages: 0 };
  }
}

/**
 * 주어진 postIds(최대 100개) 중 현재 발행 상태인 글의 제목과 주소를 조회합니다.
 * 비공개로 전환되었거나 삭제된 글은 결과에서 제외됩니다.
 */
export async function getPublishedTitles(postIds: string[]): Promise<PublishedTitle[]> {
  const ids = [...new Set(postIds)];
  if (ids.length === 0) return [];
  return apiServer.get<PublishedTitle[]>(`/api/posts/titles?ids=${ids.map(enc).join(',')}`, {
    requireAuth: false,
  });
}

export type PublishedPostLookup = { post: PublishedPostView } | { moved: MovedPost } | null;

/**
 * 현재 또는 이전 슬러그로 발행된 글을 조회합니다.
 * 이전 슬러그의 301 응답은 자동으로 따라가지 않고 반환하여 페이지가 현재 주소로 안내하도록 합니다. (01-rules, 옛 slug)
 */
export const getPublishedPost = cache(
  async (blogSlug: string, postSlug: string): Promise<PublishedPostLookup> => {
    const response = await fetch(
      `${getApiBaseUrl()}/api/blogs/${enc(blogSlug)}/posts/${enc(postSlug)}`,
      {
        redirect: 'manual',
        cache: 'no-store',
      }
    );
    if (response.status === 404) return null;
    if (response.status === 301) return { moved: (await response.json()) as MovedPost };
    if (!response.ok)
      throw ApiError.from(response.status, await response.json().catch(() => undefined));
    return { post: (await response.json()) as PublishedPostView };
  }
);

// 블로그 소유자용 관리 API입니다. (흐름 3~5)

export const getMyPost = cache(async (postId: string): Promise<PostView | null> => {
  try {
    return await apiServer.get<PostView>(`/api/me/posts/${enc(postId)}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
});

/** 모든 상태의 내 블로그 글을 최근 수정순으로 조회합니다. */
export const getMyPosts = cache(async (blogSlug: string, page: number): Promise<PostPage> =>
  apiServer.get<PostPage>(`/api/me/blogs/${enc(blogSlug)}/posts?page=${page}`)
);

async function changed<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    const data = await call();
    revalidatePath('/dashboard/posts');
    return { success: true, data };
  } catch (error) {
    return toActionError(error);
  }
}

/** 새 임시 글을 작성합니다. 발행은 publishPostAction으로 별도 수행합니다. */
export async function writePostAction(
  blogSlug: string,
  request: PostRequest
): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/blogs/${enc(blogSlug)}/posts`, request));
}

/** 글 내용을 수정하며 기존 발행 상태를 그대로 유지합니다. */
export async function revisePostAction(
  postId: string,
  request: PostRequest
): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.put<PostView>(`/api/posts/${enc(postId)}`, request));
}

/** 임시 글을 독자에게 공개 발행합니다. */
export async function publishPostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/publish`, {}));
}

/** 발행된 글을 비공개 상태로 전환합니다. */
export async function hidePostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/hide`, {}));
}

/** 비공개 상태의 글을 다시 공개합니다. */
export async function republishPostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/republish`, {}));
}

export async function deletePostAction(postId: string): Promise<ActionResult<void>> {
  return changed(() => apiServer.delete<void>(`/api/posts/${enc(postId)}`));
}
