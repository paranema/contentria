import React from 'react';

import DashboardHeader from '@/app/dashboard/_components/DashboardHeader';
import DashboardSidebar from '@/app/dashboard/_components/DashboardSidebar';
import Footer from '@/app/_components/Footer';
import { getMyBlogs } from '@/features/publishing/api/blogs';

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const blogs = await getMyBlogs();

  // 블로그가 없는 회원도 구독 목록과 계정 설정은 이용할 수 있으므로 레이아웃에서는 리다이렉트하지 않습니다.
  // 블로그가 필수인 하위 페이지(홈, 글 관리, 카테고리 관리)에서 개별적으로 /dashboard/welcome으로 이동시킵니다.
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
