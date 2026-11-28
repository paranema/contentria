import DashboardNav from './DashboardNav';
import DashboardProfileCard from './DashboardProfileCard';
import { BlogView } from '@/features/publishing/types';

interface DashboardSidebarProps {
  blogs: BlogView[];
}

/**
 * 프로필 카드와 관리 메뉴 카드를 세로로 배치한 데스크톱 전용 사이드바입니다.
 * 모바일에서는 헤더의 DashboardMobileNav 드로어가 동일한 구성을 제공합니다.
 */
export default function DashboardSidebar({ blogs }: DashboardSidebarProps) {
  const firstBlogSlug = blogs.length > 0 ? blogs[0].slug : null;

  return (
    <aside className="hidden w-64 flex-col gap-4 md:flex">
      {/* 정사각형 프로필 이미지가 카드 상단 모서리 곡률을 따르도록 overflow-hidden을 적용합니다. */}
      <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
        <DashboardProfileCard blogSlug={firstBlogSlug} />
      </div>

      <div className="flex-1 rounded-lg border bg-white pb-4 shadow-sm">
        <DashboardNav blogSlug={firstBlogSlug} />
      </div>
    </aside>
  );
}
