'use client';

import { PATHS } from '@/shared/constants/paths';
import { useUserProfile } from '@/hooks/queries/useUserQuery';
import { useAuthStore } from '@/store/authStore';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export default function CallbackClient() {
  const router = useRouter();
  const initializeAuth = useAuthStore((state) => state.initializeAuth);

  const { data: user, isSuccess, isError, error } = useUserProfile();

  useEffect(() => {
    if (isSuccess && user) {
      console.log('[Auth Callback] Successfully fetched user, initializing auth state.');
      initializeAuth(user);
      window.location.href = PATHS.HOME;
    } else if (isError) {
      console.error('[Auth Callback] Failed to fetch user after OIDC login:', error);
      router.replace(PATHS.LOGIN);
    }
  }, [isSuccess, isError, user, error, initializeAuth, router]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gray-50">
      <Loader2 className="h-12 w-12 animate-spin text-indigo-600" />
      <p className="mt-4 text-lg text-gray-700">Signing you in...</p>
    </div>
  );
}
