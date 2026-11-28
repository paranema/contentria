import { redirect } from 'next/navigation';
import { getMyBlogs } from '@/features/publishing/api/blogs';
import DashboardHeader from '@/app/dashboard/_components/DashboardHeader';
import Footer from '@/app/_components/Footer';

export default async function EditorLayout({ children }: { children: React.ReactNode }) {
  const blogs = await getMyBlogs();

  // 블로그가 없는 상태에서 URL로 직접 접근하는 경우 온보딩 화면으로 이동시킵니다.
  if (blogs.length === 0) {
    redirect('/dashboard/welcome');
  }

  const firstBlogSlug = blogs[0].slug;

  return (
    <>
      <div className="flex min-h-screen flex-col bg-gray-50 antialiased">
        <DashboardHeader blogSlug={firstBlogSlug} />
        <main className="flex flex-1 flex-col">{children}</main>
      </div>
      <Footer />
    </>
  );
}
