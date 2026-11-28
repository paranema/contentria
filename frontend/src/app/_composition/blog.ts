import { getPublicProfiles } from '@/features/member/api/profile';
import { PublicProfile } from '@/features/member/types';
import { getBlog } from '@/features/publishing/api/blogs';
import { getCategoryTree } from '@/features/publishing/api/categories';
import { BlogView, CategoryView } from '@/features/publishing/types';
import { cache } from 'react';

/** 공개 블로그의 공통 레이아웃 데이터입니다. (strategic/04-context-map.md, 조회 조합: 블로그 레이아웃) */
export interface BlogLayoutData {
  blog: BlogView;
  owner: PublicProfile | null;
  categories: CategoryView[];
}

/**
 * publishing의 블로그·카테고리와 member의 소유자 프로필을 조합해 반환합니다.
 * 레이아웃, 페이지, 사이드바 슬롯에서 중복 호출되므로 요청 단위로 캐싱하며, 블로그가 없으면 null을 반환합니다.
 */
export const getBlogLayoutData = cache(async (blogSlug: string): Promise<BlogLayoutData | null> => {
  const [blog, tree] = await Promise.all([getBlog(blogSlug), getCategoryTree(blogSlug)]);
  if (!blog) return null;
  const [owner] = await getPublicProfiles([blog.ownerId]);
  return { blog, owner: owner ?? null, categories: tree?.categories ?? [] };
});
