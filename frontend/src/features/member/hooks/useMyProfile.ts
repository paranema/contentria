'use client';

import { useQuery } from '@tanstack/react-query';
import { getMyProfileOrNull } from '../api/profile';
import { memberKeys } from './keys';

/** 로그인한 회원의 프로필을 조회하며 비로그인 시 null을 반환합니다. 루트 레이아웃에서 미리 패치합니다. */
export const useMyProfile = () =>
  useQuery({
    queryKey: memberKeys.profile(),
    queryFn: () => getMyProfileOrNull(),
    staleTime: 5 * 60 * 1000,
    retry: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
