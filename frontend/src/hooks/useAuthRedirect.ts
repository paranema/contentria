import { PATHS } from '@/shared/constants/paths';
import { useAuthStore } from '@/store/authStore';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect } from 'react';

export const useAuthRedirect = () => {
  const router = useRouter();
  const searchParams = useSearchParams();
  const isLoading = useAuthStore((state) => state.isLoading);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (isLoading) {
      return;
    }

    if (isAuthenticated) {
      const redirectUrl = searchParams.get('redirect') || PATHS.DASHBOARD;
      router.replace(redirectUrl);
    }
  }, [router, isLoading, isAuthenticated, searchParams]);

  return isLoading;
};
