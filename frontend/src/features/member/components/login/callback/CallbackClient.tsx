'use client';

import { PATHS } from '@/shared/constants/paths';
import { useMyProfile } from '../../../hooks/useMyProfile';
import { Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export default function CallbackClient() {
  const router = useRouter();
  // The API set the session cookies before sending the browser here; the profile proves it.
  const { data: profile, isSuccess, isError } = useMyProfile();

  useEffect(() => {
    if (isSuccess && profile) {
      // A full load, so that every server component renders signed in.
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
