'use client';

import { useQuery } from '@tanstack/react-query';
import { getMyProfileOrNull } from '../api/profile';
import { memberKeys } from './keys';

/** The signed-in member's profile, null for someone not signed in. The root layout prefetches it. */
export const useMyProfile = () =>
  useQuery({
    queryKey: memberKeys.profile(),
    queryFn: () => getMyProfileOrNull(),
    staleTime: 5 * 60 * 1000,
    retry: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
