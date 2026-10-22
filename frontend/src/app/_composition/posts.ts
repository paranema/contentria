import { getPublicProfiles } from '@/features/member/api/profile';
import { PublicProfile } from '@/features/member/types';
import { PostListItem } from '@/features/publishing/types';

/** A listed post with its author's profile (04-context-map, 조회 조합: 글 목록). */
export interface PostWithAuthor extends PostListItem {
  author: PublicProfile | null;
}

/** One call for every author on the page, never one per post. */
export async function withAuthors(items: PostListItem[]): Promise<PostWithAuthor[]> {
  const profiles = await getPublicProfiles(items.map((item) => item.authorId));
  const byId = new Map(profiles.map((profile) => [profile.accountId, profile]));
  return items.map((item) => ({ ...item, author: byId.get(item.authorId) ?? null }));
}
