'use client';

import Link from 'next/link';
import UserAvatar from '@/app/_components/UserAvatar';
import { useMyProfile } from '@/features/member/hooks/useMyProfile';

interface DashboardProfileCardProps {
  blogSlug: string | null;
}

/**
 * 사이드바 상단 프로필 카드: 프로필 이미지 + 닉네임 + 블로그 주소.
 * 주소 클릭 시 (기존 '블로그로 돌아가기'처럼) 내 블로그를 새 탭으로 연다.
 */
export default function DashboardProfileCard({ blogSlug }: DashboardProfileCardProps) {
  const { data: user } = useMyProfile();

  return (
    <div className="flex flex-col text-center">
      {/* 카드 폭을 가득 채우는 정사각형 프로필 이미지 (티스토리식).
          relative 필수: UserAvatar의 next/image가 fill 모드라 위치 지정 조상을 채운다 */}
      <div className="relative aspect-square w-full overflow-hidden">
        <UserAvatar user={user} size={256} shape="square" />
      </div>

      <div className="space-y-1 px-4 py-4">
        <p className="truncate text-sm font-semibold text-gray-900">{user?.displayName}</p>

        {blogSlug && (
          <Link
            href={`/@${blogSlug}`}
            target="_blank"
            className="block truncate text-xs text-muted-foreground hover:text-primary hover:underline"
          >
            contentria.com/@{blogSlug}
          </Link>
        )}
      </div>
    </div>
  );
}
