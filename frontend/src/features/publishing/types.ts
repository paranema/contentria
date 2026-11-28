// 퍼블리싱 컨텍스트 백엔드 API의 요청·응답 모델입니다. (docs/contexts/publishing/02-implementation.md)

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

/** 상위 카테고리와 각 하위 카테고리가 표시 순서대로 나열된 카테고리 트리 응답입니다. */
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

/** 편집기가 불러온 버전과 함께 전송하는 전체 카테고리 트리 수정 요청입니다. */
export interface EditCategoryTreeRequest {
  version: number;
  categories: { key: string; name: string; parentKey: string | null }[];
}

/** 게시글 상태입니다. DRAFT는 임시 저장, PUBLISHED는 공개 발행, PRIVATE은 발행 후 비공개 전환 상태입니다. */
export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'PRIVATE';

/** 블로그 소유자용 게시글 상세 정보입니다. */
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
  /** 블로그 소유자 회원 ID입니다. */
  authorId: string;
}

/** 20개 단위로 조회되는 게시글 목록 페이지 응답이며, page는 0부터 시작합니다. */
export interface PostPage {
  items: PostListItem[];
  page: number;
  totalItems: number;
  totalPages: number;
}

/** 독자에게 공개되는 발행 게시글 상세 정보입니다. */
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

/** 이전 슬러그로 조회했을 때 안내할 현재 게시글 주소 정보입니다. (01-rules, 글 slug) */
export interface MovedPost {
  blogSlug: string;
  currentSlug: string;
}

/** 게시글 ID로 제목과 공개 주소를 조회한 요약 정보입니다. */
export interface PublishedTitle {
  postId: string;
  blogSlug: string;
  title: string;
  slug: string;
}
