'use client';

import { PATHS } from '@/shared/constants/paths';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';
import { memberKeys } from './keys';

/**
 * 모든 로그인 수단의 완료 후 처리를 수행합니다.
 * 세션 쿠키가 설정된 상태에서 프로필 캐시를 무효화하고, 원래 이동하려던 경로(`proxy.ts`가 설정한 `?redirect=`)나 대시보드로 이동합니다.
 */
export function useSignedIn() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: memberKeys.profile() });
    router.replace(redirectTarget());
  }, [queryClient, router]);
}

/** 오픈 리다이렉트를 방지하기 위해 현재 사이트 내부 경로(`/`로 시작하고 `//`가 아닌 경로)만 허용합니다. */
function redirectTarget(): string {
  const requested = new URLSearchParams(window.location.search).get('redirect');
  return requested && requested.startsWith('/') && !requested.startsWith('//')
    ? requested
    : PATHS.DASHBOARD;
}
