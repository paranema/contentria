'use client';

import { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { useRouter } from 'next/navigation';

import ProfileDropdown from '@/components/dashboard/header/ProfileDropdown';
import UserAvatar from '@/components/dashboard/header/UserAvatar';
import { PATHS } from '@/shared/constants/paths';
import { logoutAction } from '@/actions/auth';
import { useUserProfile } from '@/hooks/queries/useUserQuery';
import { useQueryClient } from '@tanstack/react-query';
import { userKeys } from '@/hooks/queries/keys';

interface BlogHeaderProps {
  blogSlug: string;
}

export default function BlogHeader({ blogSlug }: BlogHeaderProps) {
  const { data: user } = useUserProfile();

  const queryClient = useQueryClient();
  const router = useRouter();

  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setIsProfileOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = async () => {
    queryClient.setQueryData(userKeys.profile(), null);

    await logoutAction();

    setIsProfileOpen(false);

    router.refresh();
  };

  const blogHomeLink = `/@${blogSlug}`;

  return (
    <header className="w-full border-b bg-white shadow-sm">
      <div className="flex items-center justify-between px-4 py-3 sm:px-6">
        {/* 좌측 영역 */}
        <div className="flex min-w-0 items-center">
          <Link href="/" className="flex flex-shrink-0 items-center space-x-2">
            <Image
              src="/contentria-icon.png"
              alt="Contentria 로고"
              width={32} // 헤더에 맞는 적절한 크기 지정
              height={32}
              priority // 로고 이미지는 우선 로드
            />
          </Link>
        </div>

        {/* 중앙 영역 */}
        <h1 className="text-2xl font-bold">
          <Link href={blogHomeLink}>{'Contentria'}</Link>
        </h1>

        {/* 우측 영역 */}
        <div className="flex items-center space-x-2 md:space-x-4">
          {/* <button
            onClick={openSubscribeModal}
            className="hidden rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:block"
          >
            구독하기
          </button> */}

          {user ? (
            <div className="relative" ref={profileRef}>
              <button
                onClick={() => setIsProfileOpen(!isProfileOpen)}
                className="relative flex h-9 w-9 items-center justify-center overflow-hidden rounded-full focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                aria-label="프로필 메뉴 열기"
              >
                <UserAvatar user={user} />
              </button>

              {isProfileOpen && (
                <ProfileDropdown
                  user={user}
                  onClose={() => setIsProfileOpen(false)}
                  onLogout={handleLogout}
                />
              )}
            </div>
          ) : (
            <Link
              href={PATHS.LOGIN}
              className="rounded-md px-3 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100"
            >
              로그인
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
