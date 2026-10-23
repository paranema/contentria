'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { Search, SquarePen } from 'lucide-react';
import { Button } from '@/shared/ui/button';
import { useRouter } from 'next/navigation';
import UserAvatar from '@/app/_components/UserAvatar';
import ProfileDropdown from '@/app/_components/ProfileDropdown';
import DashboardMobileNav from './DashboardMobileNav';
import { useMyProfile } from '@/features/member/hooks/useMyProfile';
import { useQueryClient } from '@tanstack/react-query';
import { memberKeys } from '@/features/member/hooks/keys';
import { logoutAction } from '@/features/member/api/auth';
import { PATHS } from '@/shared/constants/paths';
import NotificationBell from '@/features/notification/components/NotificationBell';

interface DashboardHeaderProps {
  blogSlug: string | null;
  /** (main) 레이아웃에서만 켜는 모바일 내비 Sheet 트리거 — 에디터/온보딩 헤더엔 없음 */
  showMobileNav?: boolean;
  /** (main) 레이아웃에서만 켜는 글쓰기 CTA — 에디터/온보딩 헤더엔 없음 */
  showWriteButton?: boolean;
}

export default function DashboardHeader({
  blogSlug,
  showMobileNav = false,
  showWriteButton = false,
}: DashboardHeaderProps) {
  const { data: user } = useMyProfile();
  const queryClient = useQueryClient();
  const router = useRouter();

  const [isSearchVisible, setIsSearchVisible] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  const profileRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        profileRef.current &&
        !(profileRef.current as HTMLElement).contains(event.target as Node)
      ) {
        setIsProfileOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = useCallback(async () => {
    queryClient.setQueryData(memberKeys.profile(), null);
    setIsProfileOpen(false);

    try {
      await logoutAction();
      router.replace(PATHS.HOME);
    } catch (e) {
      console.error('Logout error:', e);
      router.replace(PATHS.HOME);
    }
  }, [queryClient, router]);

  return (
    <header className="sticky top-0 z-10 w-full border-b bg-white shadow-sm">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
        {/* 왼쪽 영역: 모바일 내비 + 블로그 제목 & 검색 전환 */}
        <div className="flex items-center md:w-64">
          {showMobileNav && <DashboardMobileNav blogSlug={blogSlug} />}

          <Link href="/dashboard" className="text-xl font-bold md:hidden">
            Contentria
          </Link>

          {/* <div className={`relative ${isSearchVisible ? 'block' : 'hidden'} md:block`}>
            <div className="absolute inset-y-0 left-0 flex items-center pl-3">
              <Search size={18} className="text-gray-400" />
            </div>
            <input
              type="text"
              placeholder="검색..."
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 md:w-64"
            />
          </div> */}

          {!isSearchVisible && (
            <button
              onClick={() => setIsSearchVisible(true)}
              className="rounded-full p-2 hover:bg-gray-100 md:hidden"
            >
              <Search size={20} />
            </button>
          )}
        </div>

        {/* 중앙 영역: 블로그 제목 (데스크톱에서만) */}
        <h1 className="hidden text-2xl font-bold md:block">Contentria</h1>

        {/* 오른쪽 영역: 버튼 및 프로필 */}
        <div className="flex items-center space-x-2">
          {/* 주 행동(글쓰기) CTA — 페이지마다 분산돼 있던 새 글 작성 버튼을 헤더로 일원화.
              에디터(초안 유실 위험)/온보딩(블로그 없음)에서는 숨긴다 */}
          {showWriteButton && (
            <Button asChild size="sm" className="mr-1">
              <Link href="/dashboard/posts/new">
                <SquarePen className="h-4 w-4" />
                <span className="hidden sm:inline">글쓰기</span>
              </Link>
            </Button>
          )}

          {/* 알림 버튼 */}
          <NotificationBell />

          {/* 프로필 드롭다운 */}
          <div className="relative" ref={profileRef}>
            <button
              onClick={() => setIsProfileOpen(!isProfileOpen)}
              className="relative flex h-10 w-10 items-center justify-center overflow-hidden rounded-full border-2 border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <UserAvatar user={user} size={36} />
            </button>

            {isProfileOpen && (
              <ProfileDropdown
                user={user}
                onClose={() => setIsProfileOpen(false)}
                onLogout={handleLogout}
              />
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
