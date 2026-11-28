import { getPublicProfiles } from '@/features/member/api/profile';
import { PublicProfile } from '@/features/member/types';
import { PostListItem } from '@/features/publishing/types';

/** 글 목록 항목에 작성자 프로필을 결합한 데이터입니다. (04-context-map, 조회 조합: 글 목록) */
export interface PostWithAuthor extends PostListItem {
  author: PublicProfile | null;
}

/** 페이지 내 모든 글의 작성자 프로필을 한 번의 요청으로 일괄 조회해 결합합니다. */
export async function withAuthors(items: PostListItem[]): Promise<PostWithAuthor[]> {
  const profiles = await getPublicProfiles(items.map((item) => item.authorId));
  const byId = new Map(profiles.map((profile) => [profile.accountId, profile]));
  return items.map((item) => ({ ...item, author: byId.get(item.authorId) ?? null }));
}
