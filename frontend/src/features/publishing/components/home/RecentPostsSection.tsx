import Link from 'next/link';
import { RecentPostResponse } from '@/types/api/posts';

/** 빈 피드는 "죽은 플랫폼" 신호라, 이 개수 미만이면 섹션 자체를 렌더링하지 않는다 */
const MIN_POSTS_TO_SHOW = 3;

const formatDate = (iso: string | null) => {
  if (!iso) return '';
  const date = new Date(iso);
  return `${date.getFullYear()}. ${date.getMonth() + 1}. ${date.getDate()}.`;
};

/**
 * 홈 "최근 발행된 글" — 전체 블로그의 최신 공개 글.
 * 콘텐츠가 1명 → N명으로 늘어도 자연스럽게 확장되는 발견(discovery) 창구의 시작점.
 */
export default function RecentPostsSection({ posts }: { posts: RecentPostResponse[] }) {
  if (posts.length < MIN_POSTS_TO_SHOW) {
    return null;
  }

  return (
    <section className="bg-gray-50 py-16 md:py-24">
      <div className="container mx-auto max-w-6xl px-6">
        <h2 className="mb-10 text-center text-2xl font-bold text-gray-900 sm:text-3xl md:mb-12 md:text-4xl">
          최근 발행된 <span className="text-primary">글</span>
        </h2>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 sm:gap-5 lg:grid-cols-3">
          {posts.map((post) => (
            <Link
              key={`${post.blogSlug}/${post.postSlug}`}
              href={`/@${post.blogSlug}/${post.postSlug}`}
              className="flex flex-col rounded-xl border border-gray-100 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md"
            >
              <h3 className="mb-2 line-clamp-2 break-keep font-semibold text-gray-900">
                {post.title}
              </h3>
              <p className="mb-4 line-clamp-2 break-keep text-sm text-gray-600">{post.summary}</p>
              <div className="mt-auto flex items-center justify-between text-xs text-gray-400">
                <span className="truncate font-medium text-gray-500">{post.authorNickname}</span>
                <span className="shrink-0">{formatDate(post.publishedAt)}</span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
