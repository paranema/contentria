'use client';

import { usePathname } from 'next/navigation';
import { Bell, BellRing, FileText, FolderTree, Home, Settings } from 'lucide-react';
import SidebarMenuItem from './DashboardSidebarMenuItem';

interface DashboardNavProps {
  blogSlug: string | null;
  /** 모바일 Sheet에서 메뉴 클릭 시 드로어를 닫기 위한 콜백 */
  onNavigate?: () => void;
}

const navItems = [
  {
    path: '/dashboard',
    // "대시보드 안의 대시보드 메뉴"라는 자기 지칭이 어색해 티스토리식 명칭을 따른다
    label: '블로그관리 홈',
    icon: <Home size={20} />,
    requiresBlog: false,
  },
  {
    path: '/dashboard/posts',
    label: '글 관리',
    icon: <FileText size={20} />,
    requiresBlog: true,
  },
  {
    path: '/dashboard/categories',
    label: '카테고리 관리',
    icon: <FolderTree size={20} />,
    requiresBlog: true,
  },
  {
    path: '/dashboard/notifications',
    label: '알림',
    icon: <Bell size={20} />,
    requiresBlog: false,
  },
  {
    path: '/dashboard/subscriptions',
    label: '구독 중인 블로그',
    icon: <BellRing size={20} />,
    requiresBlog: false,
  },
  {
    path: '/dashboard/settings',
    label: '설정',
    icon: <Settings size={20} />,
    requiresBlog: true,
  },
];

/** 데스크톱 사이드바와 모바일 Sheet가 공유하는 대시보드 내비게이션.
 *  (블로그로 가는 링크는 상단 프로필 카드의 블로그 주소가 담당한다) */
export default function DashboardNav({ blogSlug, onNavigate }: DashboardNavProps) {
  const pathname = usePathname();

  return (
    <nav className="px-3 pt-4">
      <p className="mb-2 px-3 text-xs font-semibold uppercase text-gray-500">관리 메뉴</p>
      <ul className="space-y-1">
        {navItems.map((item) => (
          <li key={item.path}>
            <SidebarMenuItem
              path={item.path}
              label={item.label}
              icon={item.icon}
              isActive={pathname === item.path}
              disabled={item.requiresBlog && !blogSlug}
              onClick={onNavigate}
            />
          </li>
        ))}
      </ul>
    </nav>
  );
}
