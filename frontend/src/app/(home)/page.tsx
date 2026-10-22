import HeroSection from '@/components/home/HeroSection';
import EditorShowcaseSection from '@/components/home/EditorShowcaseSection';
import { getRecentPosts } from '@/features/publishing/api/posts';
import RecentPostsSection, {
  RecentPostCard,
} from '@/features/publishing/components/home/RecentPostsSection';
import { withAuthors } from '../_composition/posts';

/** How many of the latest posts the home page shows. */
const RECENT_POSTS = 6;

/** The latest posts with their authors' names; without names if member does not answer. */
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
 * 사이트 홈 — 콜드스타트 단계의 역할은 "예비 블로거 설득":
 * 1) 히어로 2) 에디터 쇼케이스(주장 대신 증거) 3) 최근 발행된 글(살아있는 사이트 신호,
 *    글이 적으면 자동 숨김). 콘텐츠가 쌓이면 3)을 발견 창구로 점진 확장한다.
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
