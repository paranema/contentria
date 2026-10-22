'use server';

import { ActionResult, toActionError } from '@/shared/api/actionResult';
import apiServer from '@/shared/api/apiServer';
import { revalidatePath } from 'next/cache';
import { CommentPage, CommentView, InteractionCounts, SubscribedBlog } from '../types';

const enc = encodeURIComponent;

async function attempt<T>(call: () => Promise<T>): Promise<ActionResult<T>> {
  try {
    return { success: true, data: await call() };
  } catch (error) {
    return toActionError(error);
  }
}

// ---------------------------------------------------------------- counts (public)

/** Like and comment counts of posts, subscriber counts of blogs; up to 100 ids of each. */
export async function getCounts(ids: {
  postIds?: string[];
  blogIds?: string[];
}): Promise<InteractionCounts> {
  const query = new URLSearchParams();
  if (ids.postIds?.length) query.set('postIds', ids.postIds.join(','));
  if (ids.blogIds?.length) query.set('blogIds', ids.blogIds.join(','));
  return apiServer.get<InteractionCounts>(`/api/interactions/counts?${query}`, {
    requireAuth: false,
  });
}

// ---------------------------------------------------------------- subscriptions (흐름 1)

/** Whether the signed-in member subscribes to the blog. */
export async function isSubscribed(blogSlug: string): Promise<boolean> {
  const status = await apiServer.get<{ subscribed: boolean }>(
    `/api/me/subscriptions/${enc(blogSlug)}`,
    {
      shouldRedirectOn401: false,
    }
  );
  return status.subscribed;
}

export async function getMySubscriptions(): Promise<SubscribedBlog[]> {
  return apiServer.get<SubscribedBlog[]>('/api/me/subscriptions');
}

export async function subscribeAction(blogSlug: string): Promise<ActionResult<void>> {
  return attempt(() => apiServer.put<void>(`/api/blogs/${enc(blogSlug)}/subscription`, {}));
}

export async function unsubscribeAction(blogSlug: string): Promise<ActionResult<void>> {
  return attempt(async () => {
    await apiServer.delete<void>(`/api/blogs/${enc(blogSlug)}/subscription`);
    revalidatePath('/dashboard/subscriptions');
  });
}

// ---------------------------------------------------------------- likes (흐름 2)

/** Which of [postIds] the signed-in member likes. */
export async function getMyLikes(postIds: string[]): Promise<Set<string>> {
  if (postIds.length === 0) return new Set();
  const liked = await apiServer.get<{ postIds: string[] }>(
    `/api/me/likes?postIds=${postIds.map(enc).join(',')}`,
    {
      shouldRedirectOn401: false,
    }
  );
  return new Set(liked.postIds);
}

export async function likeAction(postId: string): Promise<ActionResult<void>> {
  return attempt(() => apiServer.put<void>(`/api/posts/${enc(postId)}/like`, {}));
}

export async function unlikeAction(postId: string): Promise<ActionResult<void>> {
  return attempt(() => apiServer.delete<void>(`/api/posts/${enc(postId)}/like`));
}

// ---------------------------------------------------------------- comments (흐름 3)

/** A published post's comments, a page at a time; [cursor] comes from the page before. Public. */
export async function getComments(postId: string, cursor?: string | null): Promise<CommentPage> {
  const query = cursor ? `?cursor=${enc(cursor)}` : '';
  return apiServer.get<CommentPage>(`/api/posts/${enc(postId)}/comments${query}`, {
    requireAuth: false,
  });
}

/** A comment, or with [parentId] a reply to a top-level one. */
export async function writeCommentAction(
  postId: string,
  body: string,
  parentId: string | null = null
): Promise<ActionResult<CommentView>> {
  return attempt(() =>
    apiServer.post<CommentView>(`/api/posts/${enc(postId)}/comments`, { body, parentId })
  );
}

export async function editCommentAction(
  commentId: string,
  body: string
): Promise<ActionResult<CommentView>> {
  return attempt(() => apiServer.patch<CommentView>(`/api/comments/${enc(commentId)}`, { body }));
}

export async function deleteCommentAction(commentId: string): Promise<ActionResult<void>> {
  return attempt(() => apiServer.delete<void>(`/api/comments/${enc(commentId)}`));
}
