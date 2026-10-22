import { getPublicProfiles } from '@/features/member/api/profile';
import { PublicProfile } from '@/features/member/types';
import { getBlog } from '@/features/publishing/api/blogs';
import { getCategoryTree } from '@/features/publishing/api/categories';
import { BlogView, CategoryView } from '@/features/publishing/types';
import { cache } from 'react';

/**
 * What every page of a public blog shows around its content (strategic/04-context-map.md, 조회
 * 조합: 블로그 레이아웃): the blog and its categories from publishing, the owner's profile from
 * member. Null when there is no such blog. Cached per request: the layout, the page and the
 * sidebar slot all ask.
 */
export interface BlogLayoutData {
  blog: BlogView;
  owner: PublicProfile | null;
  categories: CategoryView[];
}

export const getBlogLayoutData = cache(async (blogSlug: string): Promise<BlogLayoutData | null> => {
  const [blog, tree] = await Promise.all([getBlog(blogSlug), getCategoryTree(blogSlug)]);
  if (!blog) return null;
  const [owner] = await getPublicProfiles([blog.ownerId]);
  return { blog, owner: owner ?? null, categories: tree?.categories ?? [] };
});
