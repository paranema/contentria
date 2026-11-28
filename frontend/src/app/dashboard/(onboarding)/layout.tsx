import React from 'react';

import DashboardHeader from '@/app/dashboard/_components/DashboardHeader';
import Footer from '@/app/_components/Footer';

/**
 * 블로그 개설 전 단계인 온보딩 전용 레이아웃입니다.
 * 아직 이용할 수 없는 대시보드 사이드바와 메뉴를 제외해 블로그 생성에만 집중하도록 구성합니다.
 */
export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid h-screen grid-rows-[auto_1fr_auto] bg-gray-50">
      <DashboardHeader blogSlug={null} />

      <main className="mx-auto flex w-full max-w-2xl items-center p-4">
        <div className="w-full">{children}</div>
      </main>

      <Footer />
    </div>
  );
}
