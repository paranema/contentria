'use server';

import { getPublicProfiles } from '@/features/member/api/profile';
import { getComments } from '@/features/interaction/api/interactions';
import type { CommentsWithAuthors } from '@/features/interaction/components/CommentSection';
import type { CommentView } from '@/features/interaction/types';

/**
 * A page of a post's comments with their authors (04-context-map, 조회 조합: 댓글 목록):
 * interaction's comments, member's profiles, one call for every author on the page. A server
 * action, so the comment section can ask for the next page itself.
 */
export async function loadComments(
  postId: string,
  cursor?: string | null
): Promise<CommentsWithAuthors> {
  const page = await getComments(postId, cursor);
  const ids = page.comments
    .flatMap((comment: CommentView) => [comment, ...comment.replies])
    .map((comment) => comment.commenterId)
    .filter((id): id is string => id !== null);
  const profiles = await getPublicProfiles(ids);
  return {
    page,
    authors: Object.fromEntries(
      profiles.map((p) => [p.accountId, { displayName: p.displayName, pictureUrl: p.pictureUrl }])
    ),
  };
}
