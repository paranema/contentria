import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getMyPosts } from '@/features/publishing/api/posts';
import { postColumns } from '@/features/publishing/components/dashboard/postColumns';
import CustomPagination from '@/shared/components/CustomPagination';
import { DataTable } from '@/shared/components/DataTable';
import { notFound, redirect } from 'next/navigation';

interface PostPageProps {
  searchParams: Promise<{
    page?: string;
  }>;
}

export default async function PostsPage({ searchParams }: PostPageProps) {
  const { page } = await searchParams;

  // URL 쿼리의 페이지 번호(1부터 시작)를 API 페이지 인덱스(0부터 시작)로 변환합니다.
  const currentPage = page ? Math.max(0, parseInt(page, 10) - 1) : 0;
  if (page && (isNaN(currentPage) || parseInt(page, 10) < 1)) {
    notFound();
  }

  const blogs = await getMyBlogs();
  if (blogs.length === 0) {
    redirect('/dashboard/welcome');
  }

  const postsPage = await getMyPosts(blogs[0].slug, currentPage);
  if (currentPage >= postsPage.totalPages && postsPage.totalPages > 0) {
    notFound();
  }

  return (
    <div className="space-y-6">
      {/* 새 글 작성 버튼은 상단 대시보드 헤더에 일원화되어 있습니다. */}
      <h1 className="text-2xl font-bold text-gray-800">글 관리</h1>

      <DataTable columns={postColumns} data={postsPage.items} />

      <div className="border-t border-gray-200 px-4 py-3 sm:px-6">
        <CustomPagination totalPages={postsPage.totalPages} />
      </div>
    </div>
  );
}
