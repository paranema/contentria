'use server';

import apiServer from '@/shared/api/apiServer';
import { DashboardRange, DashboardView } from '../types';

/** The owner's blog dashboard: today, yesterday and in total, the trend over [range], popular posts. */
export async function getDashboard(
  blogSlug: string,
  range: DashboardRange
): Promise<DashboardView> {
  return apiServer.get<DashboardView>(
    `/api/me/blogs/${encodeURIComponent(blogSlug)}/dashboard?range=${range}`
  );
}
