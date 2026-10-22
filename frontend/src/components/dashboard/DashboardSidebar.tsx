import DashboardNav from './DashboardNav';
import DashboardProfileCard from './DashboardProfileCard';
import { BlogView } from '@/features/publishing/types';

interface DashboardSidebarProps {
  blogs: BlogView[];
}

/**
 * Desktop-only sidebar: two stacked cards on the gray page background —
 * a profile card (avatar/nickname/blog address) and the management menu.
 * On mobile the same composition is served by DashboardMobileNav (header Sheet).
 */
export default function DashboardSidebar({ blogs }: DashboardSidebarProps) {
  const firstBlogSlug = blogs.length > 0 ? blogs[0].slug : null;

  return (
    <aside className="hidden w-64 flex-col gap-4 md:flex">
      {/* overflow-hidden: 정사각 프로필 이미지가 카드 상단 모서리 라운드를 따르게 */}
      <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
        <DashboardProfileCard blogSlug={firstBlogSlug} />
      </div>

      {/* flex-1: 메뉴 카드가 남은 세로 영역을 가득 채운다 */}
      <div className="flex-1 rounded-lg border bg-white pb-4 shadow-sm">
        <DashboardNav blogSlug={firstBlogSlug} />
      </div>
    </aside>
  );
}
