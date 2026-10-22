'use client';

import { PATHS } from '@/shared/constants/paths';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';
import { memberKeys } from './keys';

/**
 * What every way of signing in ends with: the session cookies are already set, so the profile is
 * fetched again and the browser goes where it was headed (`?redirect=`, set by proxy.ts) or to
 * the dashboard.
 */
export function useSignedIn() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: memberKeys.profile() });
    router.replace(redirectTarget());
  }, [queryClient, router]);
}

/** Only a path on this site: `?redirect=https://elsewhere` must not become an open redirect. */
function redirectTarget(): string {
  const requested = new URLSearchParams(window.location.search).get('redirect');
  return requested && requested.startsWith('/') && !requested.startsWith('//')
    ? requested
    : PATHS.DASHBOARD;
}
