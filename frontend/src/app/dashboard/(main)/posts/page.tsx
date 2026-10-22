import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getMyPosts } from '@/features/publishing/api/posts';
import { postColumns } from '@/features/publishing/components/dashboard/postColumns';
import CustomPagination from '@/shared/components/CustomPagination';
import { DataTable } from '@/shared/components/DataTable';
import { PATHS } from '@/shared/constants/paths';
import { notFound, redirect } from 'next/navigation';

interface PostPageProps {
  searchParams: Promise<{
    page?: string;
  }>;
}

export default async function PostsPage({ searchParams }: PostPageProps) {
  const { page } = await searchParams;

  // ?page= counts from 1 on screen; the API counts from 0.
  const currentPage = page ? Math.max(0, parseInt(page, 10) - 1) : 0;
  if (page && (isNaN(currentPage) || parseInt(page, 10) < 1)) {
    notFound();
  }

  const blogs = await getMyBlogs();
  if (blogs.length === 0) {
    redirect(PATHS.DASHBOARD);
  }

  const postsPage = await getMyPosts(blogs[0].slug, currentPage);
  if (currentPage >= postsPage.totalPages && postsPage.totalPages > 0) {
    notFound();
  }

  return (
    <div className="space-y-6">
      {/* 헤더 영역 — 글쓰기 CTA는 페이지가 아니라 상단 헤더에 일원화돼 있다 */}
      <h1 className="text-2xl font-bold text-gray-800">글 관리</h1>

      <DataTable columns={postColumns} data={postsPage.items} />

      <div className="border-t border-gray-200 px-4 py-3 sm:px-6">
        <CustomPagination totalPages={postsPage.totalPages} />
      </div>
    </div>
  );
}
