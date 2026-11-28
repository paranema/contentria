'use server';

import { getPublicProfiles } from '@/features/member/api/profile';
import { getComments } from '@/features/interaction/api/interactions';
import type { CommentsWithAuthors } from '@/features/interaction/components/CommentSection';
import type { CommentView } from '@/features/interaction/types';

/**
 * 게시글 댓글 한 페이지와 작성자 프로필을 일괄 조회해 조합합니다. (04-context-map, 조회 조합: 댓글 목록)
 * 댓글 섹션에서 다음 페이지를 직접 요청할 수 있도록 서버 액션으로 제공합니다.
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
