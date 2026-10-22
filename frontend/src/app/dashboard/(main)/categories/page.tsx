import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getCategoryTree } from '@/features/publishing/api/categories';
import CategoryManager from '@/features/publishing/components/dashboard/categories/CategoryManager';
import { PATHS } from '@/shared/constants/paths';
import { Metadata } from 'next';
import { redirect } from 'next/navigation';

export const metadata: Metadata = {
  title: '카테고리 관리 | 대시보드',
};

export default async function CategoriesPage() {
  const blog = (await getMyBlogs())[0];
  if (!blog) {
    redirect('/dashboard/welcome');
  }

  const tree = await getCategoryTree(blog.slug);
  if (!tree) {
    redirect(PATHS.DASHBOARD);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">카테고리 관리</h1>
        <p className="mt-1 text-sm text-gray-500">카테고리 구조를 편집하고 순서를 변경합니다.</p>
      </div>

      <div className="rounded-lg border bg-white p-4 shadow-sm sm:p-6">
        <CategoryManager blogSlug={blog.slug} initialTree={tree} />
      </div>
    </div>
  );
}
