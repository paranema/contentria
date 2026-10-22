'use client';

import Link from 'next/link';
import { useState, useEffect, useRef, useCallback } from 'react';
import Image from 'next/image';

import ProfileDropdown from './ProfileDropdown';
import UserAvatar from './UserAvatar';
import { PATHS } from '@/shared/constants/paths';
import { useMyProfile } from '@/features/member/hooks/useMyProfile';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { memberKeys } from '@/features/member/hooks/keys';
import { logoutAction } from '@/features/member/api/auth';

export default function HomeHeader() {
  const { data: user } = useMyProfile();

  const queryClient = useQueryClient();
  const router = useRouter();

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleLogout = useCallback(async () => {
    queryClient.setQueryData(memberKeys.profile(), null);
    setIsDropdownOpen(false);

    await logoutAction();

    router.refresh();
  }, [queryClient, router]);

  return (
    <header className="border-b shadow-sm">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
        {/* 좌측 영역 */}
        <div className="flex min-w-0 items-center">
          <Link href="/" className="flex flex-shrink-0 items-center space-x-2">
            <Image
              src="/contentria-icon.png"
              alt="Contentria 로고"
              width={32} // 헤더에 맞는 적절한 크기 지정
              height={32}
              priority // 헤더 로고는 항상 중요
            />
          </Link>
        </div>

        {/* 중앙 영역 */}
        <h1 className="text-2xl font-bold">
          <Link href="/">Contentria</Link>
        </h1>

        {/* 우측 영역 */}
        <div className="flex items-center space-x-2">
          <div>
            {user ? (
              <div ref={dropdownRef} className="relative">
                <button
                  type="button"
                  className="focus-ring-2 rounded-full transition-opacity duration-200 hover:opacity-90 focus:outline-none focus:ring-indigo-500 focus:ring-offset-2"
                  onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                >
                  <UserAvatar user={user} size="md" />
                </button>
                <ProfileDropdown
                  user={user}
                  isOpen={isDropdownOpen}
                  onClose={() => setIsDropdownOpen(false)}
                  onLogout={handleLogout}
                />
              </div>
            ) : (
              <Link
                href={PATHS.LOGIN}
                className="my-2 ml-2 rounded-lg bg-indigo-500 px-4 py-3 text-sm font-medium text-white shadow-sm transition-colors duration-200 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
              >
                로그인
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
