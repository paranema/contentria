'use server';

import apiServer from '@/shared/api/apiServer';
import { DashboardRange, DashboardView } from '../types';

/** 블로그 소유자의 대시보드 지표(요약 통계, 기간별 추이, 인기 게시글)를 조회합니다. */
export async function getDashboard(
  blogSlug: string,
  range: DashboardRange
): Promise<DashboardView> {
  return apiServer.get<DashboardView>(
    `/api/me/blogs/${encodeURIComponent(blogSlug)}/dashboard?range=${range}`
  );
}
