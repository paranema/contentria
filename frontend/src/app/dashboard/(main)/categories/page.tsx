import { getMyBlogAction } from '@/actions/blog';
import { getCategoriesAction } from '@/actions/category';
import CategoryManager from '@/components/dashboard/categories/CategoryManager';
import { PATHS } from '@/shared/constants/paths';
import { Metadata } from 'next';
import { redirect } from 'next/navigation';

export const metadata: Metadata = {
  title: '카테고리 관리 | 대시보드',
};

export default async function CategoriesPage() {
  const blogInfos = await getMyBlogAction();
  if (!blogInfos || blogInfos.length === 0) {
    redirect(PATHS.DASHBOARD);
  }

  const categories = await getCategoriesAction();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">카테고리 관리</h1>
        <p className="mt-1 text-sm text-gray-500">카테고리 구조를 편집하고 순서를 변경합니다.</p>
      </div>

      <div className="rounded-lg border bg-white p-4 shadow-sm sm:p-6">
        <CategoryManager initialCategories={categories} blogInfo={blogInfos[0]} />
      </div>
    </div>
  );
}
