'use client';

import { useQuery } from '@tanstack/react-query';
import { getDashboard } from '../api/dashboard';
import { analyticsKeys } from '../queryKeys';
import { DashboardRange } from '../types';

/** 서버에서 프리페치된 대시보드 전체 데이터를 조회합니다. */
export const useDashboard = (blogSlug: string, range: DashboardRange) =>
  useQuery({
    queryKey: analyticsKeys.dashboard(blogSlug, range),
    queryFn: () => getDashboard(blogSlug, range),
    staleTime: 5 * 60 * 1000,
    placeholderData: (previous) => previous,
  });
