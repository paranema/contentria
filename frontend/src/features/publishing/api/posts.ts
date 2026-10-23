'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import { API_BASE_URL } from '@/shared/api/apiBase';
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

// ---------------------------------------------------------------- readers (흐름 4)

/** A blog's published posts, newest first; with [category], that category's and its children's. */
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
 * Every blog's latest published posts, for the home page. Plain fetch without cookies so the home
 * page can stay statically rendered (revalidated every five minutes); an empty page on failure
 * so that the home page never fails for it.
 */
export async function getRecentPosts(): Promise<PostPage> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/posts?page=0`, { next: { revalidate: 300 } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as PostPage;
  } catch (error) {
    console.error('Failed to fetch recent posts:', error);
    return { items: [], page: 0, totalItems: 0, totalPages: 0 };
  }
}

/**
 * Titles and addresses of the published ones among [postIds] (up to 100), for screens that name
 * posts by id. A post hidden or deleted since is simply missing.
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
 * The published post at [postSlug], current or old. An old slug answers 301; the redirect is not
 * followed here so that the page can send the reader to the current address (01-rules, 옛 slug).
 */
export const getPublishedPost = cache(
  async (blogSlug: string, postSlug: string): Promise<PublishedPostLookup> => {
    const response = await fetch(
      `${API_BASE_URL}/api/blogs/${enc(blogSlug)}/posts/${enc(postSlug)}`,
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

// ---------------------------------------------------------------- the owner (흐름 3~5)

export const getMyPost = cache(async (postId: string): Promise<PostView | null> => {
  try {
    return await apiServer.get<PostView>(`/api/me/posts/${enc(postId)}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
});

/** Every status, most recently changed first. */
export const getMyPosts = cache(
  async (blogSlug: string, page: number): Promise<PostPage> =>
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

/** A new draft. Publishing is a separate step (publishPostAction). */
export async function writePostAction(
  blogSlug: string,
  request: PostRequest
): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/blogs/${enc(blogSlug)}/posts`, request));
}

/** Keeps the post's status: a published post stays published. */
export async function revisePostAction(
  postId: string,
  request: PostRequest
): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.put<PostView>(`/api/posts/${enc(postId)}`, request));
}

/** A draft goes out to readers. */
export async function publishPostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/publish`, {}));
}

/** A published post is hidden from readers. */
export async function hidePostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/hide`, {}));
}

/** A hidden post goes back out. */
export async function republishPostAction(postId: string): Promise<ActionResult<PostView>> {
  return changed(() => apiServer.post<PostView>(`/api/posts/${enc(postId)}/republish`, {}));
}

export async function deletePostAction(postId: string): Promise<ActionResult<void>> {
  return changed(() => apiServer.delete<void>(`/api/posts/${enc(postId)}`));
}
