import { redirect } from 'next/navigation';
import { getMyBlogs } from '@/features/publishing/api/blogs';
import DashboardHeader from '@/components/dashboard/DashboardHeader';
import Footer from '@/components/home/Footer';

export default async function EditorLayout({ children }: { children: React.ReactNode }) {
  const blogs = await getMyBlogs();

  // Same guard as (main): the editor is meaningless without a blog (direct-URL defense).
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
