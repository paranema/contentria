import React from 'react';
import { redirect } from 'next/navigation';

import DashboardHeader from '@/components/dashboard/DashboardHeader';
import DashboardSidebar from '@/components/dashboard/DashboardSidebar';
import Footer from '@/components/home/Footer';
import { getMyBlogs } from '@/features/publishing/api/blogs';

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const blogs = await getMyBlogs();

  // Central guard: every (main) route (dashboard/posts/categories/settings) requires a
  // blog. This also closes side doors like the header profile dropdown → settings.
  if (blogs.length === 0) {
    redirect('/dashboard/welcome');
  }

  const firstBlogSlug = blogs[0].slug;

  return (
    <div className="grid h-screen grid-rows-[auto_1fr_auto] bg-gray-50">
      <DashboardHeader blogSlug={firstBlogSlug} showMobileNav showWriteButton />

      <div className="mx-auto grid w-full max-w-7xl grid-cols-1 gap-6 p-4 md:grid-cols-[auto_1fr]">
        <DashboardSidebar blogs={blogs} />
        <main className="overflow-y-auto p-6 shadow-sm">{children}</main>
      </div>

      <Footer />
    </div>
  );
}
