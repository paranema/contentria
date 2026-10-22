import React from 'react';

import DashboardHeader from '@/components/dashboard/DashboardHeader';
import DashboardSidebar from '@/components/dashboard/DashboardSidebar';
import Footer from '@/components/home/Footer';
import { getMyBlogs } from '@/features/publishing/api/blogs';

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const blogs = await getMyBlogs();

  // No guard here: a member without a blog still has subscriptions and a profile. The pages
  // that need a blog (home, posts, categories) send a blog-less member to /dashboard/welcome.
  const firstBlogSlug = blogs[0]?.slug ?? null;

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
