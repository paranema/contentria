'use client';

import Link from 'next/link';
import UserAvatar from '@/app/_components/UserAvatar';
import { useMyProfile } from '@/features/member/hooks/useMyProfile';

interface DashboardProfileCardProps {
  blogSlug: string | null;
}

/**
 * 사이드바 상단에 프로필 이미지, 닉네임, 블로그 주소를 표시하는 카드입니다.
 * 블로그 주소를 클릭하면 내 블로그를 새 탭에서 엽니다.
 */
export default function DashboardProfileCard({ blogSlug }: DashboardProfileCardProps) {
  const { data: user } = useMyProfile();

  return (
    <div className="flex flex-col text-center">
      {/* UserAvatar 내부의 next/image(fill 모드)가 카드 폭 기준 정사각형 영역을 채우도록 relative를 지정합니다. */}
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
