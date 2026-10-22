'use client';

import { ApiError } from '@/shared/api/apiError';
import { useQuery } from '@tanstack/react-query';
import {
  getDashboardStatsAction,
  getPopularPostsAction,
  getTrafficDataAction,
} from '@/actions/dashboard';
import {
  TrafficChartResponse,
  PopularPostResponse,
  StatsResponse,
  TimeRange,
} from '@/types/api/dashboard';

export const useDashboadStatsQuery = (slug: string) => {
  return useQuery<StatsResponse, ApiError>({
    queryKey: ['dashboard', 'stats', slug],
    queryFn: () => getDashboardStatsAction(slug),
  });
};

export const usePopularPostsQuery = (slug: string) => {
  return useQuery<PopularPostResponse[], ApiError>({
    queryKey: ['dashboard', 'popular-posts', slug],
    queryFn: () => getPopularPostsAction(slug),
  });
};

export const useTrafficChartQuery = (slug: string, timeRange: TimeRange) => {
  return useQuery<TrafficChartResponse[], ApiError>({
    queryKey: ['dashboard', 'traffic', slug, timeRange],
    queryFn: () => getTrafficDataAction(slug, timeRange),
    staleTime: 5 * 60 * 1000, // 5 minutes
    placeholderData: (previousData) => previousData,
  });
};
