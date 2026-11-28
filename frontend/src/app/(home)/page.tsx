import HeroSection from '@/app/(home)/_components/HeroSection';
import EditorShowcaseSection from '@/app/(home)/_components/EditorShowcaseSection';
import { getRecentPosts } from '@/features/publishing/api/posts';
import RecentPostsSection, {
  RecentPostCard,
} from '@/features/publishing/components/home/RecentPostsSection';
import { withAuthors } from '../_composition/posts';

/** 홈 화면에 노출할 최신 게시글 최대 개수입니다. */
const RECENT_POSTS = 6;

/** 최신 게시글 목록에 작성자 이름을 결합하며, 회원 조회 실패 시 이름 없이 반환합니다. */
async function recentPostCards(): Promise<RecentPostCard[]> {
  const items = (await getRecentPosts()).items.slice(0, RECENT_POSTS);
  const posts = await withAuthors(items).catch(() =>
    items.map((item) => ({ ...item, author: null }))
  );
  return posts.map((post) => ({
    postId: post.postId,
    title: post.title,
    summary: post.summary,
    blogSlug: post.blogSlug,
    slug: post.slug,
    publishedAt: post.publishedAt,
    authorName: post.author?.displayName ?? null,
  }));
}

/**
 * 예비 블로거 유입을 위한 홈 화면입니다.
 * 히어로, 에디터 쇼케이스, 최근 발행 글(게시글 부족 시 자동 숨김) 순서로 구성하며, 향후 콘텐츠 탐색 영역으로 확장합니다.
 */
export default async function HomePage() {
  const recentPosts = await recentPostCards();

  return (
    <div className="bg-white">
      <HeroSection />
      <EditorShowcaseSection />
      <RecentPostsSection posts={recentPosts} />
    </div>
  );
}
