import { getCounts } from '@/features/interaction/api/interactions';
import { getPublishedPosts } from '@/features/publishing/api/posts';
import PostCard from '@/features/publishing/components/blog/PostCard';
import CustomPagination from '@/shared/components/CustomPagination';
import { getBlogLayoutData } from '../../_composition/blog';
import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import VisitTracker from '@/features/analytics/components/VisitTracker';

interface UserBlogPageProps {
  params: Promise<{
    blogSlug: string;
  }>;
  searchParams: Promise<{
    page?: string;
    category?: string;
  }>;
}

export async function generateMetadata({ params }: UserBlogPageProps): Promise<Metadata> {
  const { blogSlug } = await params;

  const layout = await getBlogLayoutData(blogSlug);
  if (!layout) {
    return {
      title: '블로그를 찾을 수 없습니다.',
      description: '요청하신 페이지를 찾을 수 없습니다.',
    };
  }

  return {
    title: `@${layout.blog.slug} | Contentria`,
    description: `${layout.owner?.displayName ?? layout.blog.title}님의 블로그입니다. 다양한 글들을 만나보세요.`,
  };
}

export default async function BlogPage({ params, searchParams }: UserBlogPageProps) {
  const { blogSlug } = await params;
  const { page, category } = await searchParams;

  const currentPage = page ? Math.max(0, parseInt(page, 10) - 1) : 0;
  if (page && (isNaN(currentPage) || parseInt(page, 10) < 1)) {
    notFound();
  }

  const layout = await getBlogLayoutData(blogSlug);
  if (!layout) {
    notFound();
  }
  // A category this blog does not have answers 404 like a blog that does not exist.
  if (category && !layout.categories.some((c) => c.slug === category)) {
    notFound();
  }

  const postsPage = await getPublishedPosts(blogSlug, currentPage, category);
  if (currentPage >= postsPage.totalPages && postsPage.totalPages > 0) {
    notFound();
  }

  const posts = postsPage.items;
  // Interaction's counts for the whole page at once (04-context-map, 조회 조합: 글 목록)
  const counts = await getCounts({ postIds: posts.map((post) => post.postId) });
  const totalPages = postsPage.totalPages;
  const categoryName = layout.categories.find((c) => c.slug === category)?.name;

  return (
    <>
      <VisitTracker blogId={layout.blog.blogId} />

      <div className="w-full max-w-4xl">
        {category ? (
          <h1 className="mb-8 text-2xl font-bold sm:text-3xl">'{categoryName}' 카테고리 글</h1>
        ) : (
          <h1 className="mb-8 text-2xl font-bold sm:text-3xl">최신 글</h1>
        )}

        <div className="flex flex-col divide-y divide-gray-100">
          {posts.length > 0 ? (
            posts.map((post) => (
              <PostCard
                key={post.postId}
                blogSlug={blogSlug}
                post={post}
                counts={{
                  likes: counts.likes[post.postId] ?? 0,
                  comments: counts.comments[post.postId] ?? 0,
                }}
              />
            ))
          ) : (
            <div className="flex h-40 items-center justify-center rounded-lg border-2 border-dashed border-gray-200 bg-gray-50">
              <p className="text-sm text-gray-500">아직 작성된 글이 없습니다.</p>
            </div>
          )}
        </div>

        <div className="mt-12">
          <CustomPagination totalPages={totalPages} />
        </div>
      </div>
    </>
  );
}
