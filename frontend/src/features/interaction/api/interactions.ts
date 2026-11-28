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

/** 게시글의 좋아요·댓글 수와 블로그의 구독자 수를 각 최대 100개 ID까지 공개 조회합니다. */
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

/** 로그인한 회원의 해당 블로그 구독 여부를 조회합니다. (interaction 01-rules 흐름 1) */
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

/** 주어진 게시글 ID 목록 중 로그인한 회원이 좋아요를 누른 게시글 ID 집합을 반환합니다. (interaction 01-rules 흐름 2) */
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

/** 발행된 게시글의 댓글 목록을 커서 기반 페이지 단위로 공개 조회합니다. (interaction 01-rules 흐름 3) */
export async function getComments(postId: string, cursor?: string | null): Promise<CommentPage> {
  const query = cursor ? `?cursor=${enc(cursor)}` : '';
  return apiServer.get<CommentPage>(`/api/posts/${enc(postId)}/comments${query}`, {
    requireAuth: false,
  });
}

/** 새 댓글을 작성하거나, parentId가 주어지면 최상위 댓글에 답글을 작성합니다. */
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
