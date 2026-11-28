'use client';

import { PATHS } from '@/shared/constants/paths';
import { useMyProfile } from '../../../hooks/useMyProfile';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export default function CallbackClient() {
  const router = useRouter();
  // API가 브라우저를 이곳으로 보내기 전에 설정한 세션 쿠키가 유효한지 프로필 조회로 확인합니다.
  const { data: profile, isSuccess, isError } = useMyProfile();

  useEffect(() => {
    if (isSuccess && profile) {
      // 모든 서버 컴포넌트가 로그인 상태로 다시 렌더링되도록 전체 페이지를 새로 로드합니다.
      window.location.href = PATHS.HOME;
    } else if (isError || (isSuccess && !profile)) {
      router.replace(PATHS.LOGIN);
    }
  }, [isSuccess, isError, profile, router]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gray-50">
      <Loader2 className="h-12 w-12 animate-spin text-indigo-600" />
      <p className="mt-4 text-lg text-gray-700">Signing you in...</p>
    </div>
  );
}
