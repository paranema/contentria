// The publishing context's API, as backend/contexts/publishing answers it
// (docs/contexts/publishing/02-implementation.md).

export interface BlogView {
  blogId: string;
  ownerId: string;
  slug: string;
  title: string;
  description: string | null;
}

export interface CreateBlogRequest {
  slug: string;
  title: string;
  description?: string | null;
}

export interface ChangeBlogSettingsRequest {
  title: string;
  description: string | null;
}

/** Top-level categories, each followed by its children, in display order. */
export interface CategoryTreeView {
  blogId: string;
  version: number;
  categories: CategoryView[];
}

export interface CategoryView {
  id: string;
  name: string;
  slug: string;
  parentId: string | null;
  displayOrder: number;
}

/** The whole tree, as the editor sends it back with the version it loaded. */
export interface EditCategoryTreeRequest {
  version: number;
  categories: { key: string; name: string; parentKey: string | null }[];
}

/** DRAFT: never published. PRIVATE: published once, hidden again. Only PUBLISHED reaches readers. */
export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'PRIVATE';

/** What the owner edits. */
export interface PostView {
  postId: string;
  blogId: string;
  categoryId: string | null;
  title: string;
  body: string;
  summary: string;
  summaryWrittenByAuthor: boolean;
  status: PostStatus;
  publishedAt: string | null;
  slug: string;
  oldSlugs: string[];
  videoId: string | null;
}

export interface PostRequest {
  title: string;
  body: string;
  summary?: string | null;
  categoryId: string | null;
  videoId: string | null;
}

export interface PostListItem {
  postId: string;
  blogSlug: string;
  categoryId: string | null;
  title: string;
  summary: string;
  status: PostStatus;
  publishedAt: string | null;
  slug: string;
  updatedAt: string;
  /** The blog's owner; the screen asks member for the profile. */
  authorId: string;
}

/** A page of a list, 20 at a time. [page] counts from 0. */
export interface PostPage {
  items: PostListItem[];
  page: number;
  totalItems: number;
  totalPages: number;
}

/** What readers see of a published post. */
export interface PublishedPostView {
  postId: string;
  blogId: string;
  blogSlug: string;
  categoryId: string | null;
  title: string;
  body: string;
  summary: string;
  publishedAt: string;
  slug: string;
  authorId: string;
}

/** An old slug: the post now lives at [currentSlug] (01-rules, 글 slug). */
export interface MovedPost {
  blogSlug: string;
  currentSlug: string;
}

/** A published post's title and address, for screens that name posts by id. */
export interface PublishedTitle {
  postId: string;
  blogSlug: string;
  title: string;
  slug: string;
}
