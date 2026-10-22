import { getMyBlogAction } from '@/actions/blog';
import { PATHS } from '@/shared/constants/paths';
import { notFound, redirect } from 'next/navigation';
import { getBlogPostsAction } from '@/actions/post';
import CustomPagination from '@/shared/components/CustomPagination';
import { DataTable } from '@/shared/components/DataTable';
import { columns } from '@/components/dashboard/posts/columns';

interface PostPageProps {
  searchParams: Promise<{
    page?: string;
  }>;
}

export default async function PostsPage({ searchParams }: PostPageProps) {
  const { page } = await searchParams;
  const currentPage = page ? Math.max(0, parseInt(page, 10) - 1) : 0;

  if (page && (isNaN(currentPage) || parseInt(page, 10) < 1)) {
    notFound();
  }

  const blogInfos = await getMyBlogAction();
  if (!blogInfos || blogInfos.length === 0) {
    redirect(PATHS.DASHBOARD);
  }

  const postsPage = await getBlogPostsAction(blogInfos[0].slug, {
    statuses: ['PUBLISHED', 'DRAFT'],
    page: currentPage,
    size: 10,
  });

  if (postsPage && currentPage >= postsPage.page.totalPages && postsPage.page.totalPages > 0) {
    notFound();
  }
  const initialPosts = postsPage?.content ?? [];
  const totalPages = postsPage?.page.totalPages ?? 0;

  return (
    <div className="space-y-6">
      {/* 헤더 영역 — 글쓰기 CTA는 페이지가 아니라 상단 헤더에 일원화돼 있다 */}
      <h1 className="text-2xl font-bold text-gray-800">글 관리</h1>

      <DataTable columns={columns} data={initialPosts} />
      <div className="border-t border-gray-200 px-4 py-3 sm:px-6">
        <CustomPagination totalPages={totalPages} />
      </div>
    </div>
  );
}
