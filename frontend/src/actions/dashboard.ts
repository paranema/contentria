'use server';

import apiServer from '@/shared/api/apiServer';
import {
  TrafficChartResponse,
  PopularPostResponse,
  StatsResponse,
  TimeRange,
} from '@/types/api/dashboard';

export async function getDashboardStatsAction(blogSlug: string): Promise<StatsResponse> {
  return await apiServer.get<StatsResponse>(`/api/blogs/${blogSlug}/dashboard/stats`, {
    requireAuth: true,
  });
}

export async function getPopularPostsAction(
  blogSlug: string,
  size = 10
): Promise<PopularPostResponse[]> {
  return await apiServer.get<PopularPostResponse[]>(
    `/api/blogs/${blogSlug}/dashboard/popular-posts?size=${size}`,
    {
      requireAuth: true,
    }
  );
}

export async function getTrafficDataAction(
  blogSlug: string,
  timeRange: TimeRange
): Promise<TrafficChartResponse[]> {
  const query = new URLSearchParams({ timeRange });

  return await apiServer.get<TrafficChartResponse[]>(
    `/api/blogs/${blogSlug}/dashboard/traffic?${query.toString()}`,
    {
      requireAuth: true,
    }
  );
}
