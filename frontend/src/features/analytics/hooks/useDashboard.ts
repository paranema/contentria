'use client';

import { useQuery } from '@tanstack/react-query';
import { getDashboard } from '../api/dashboard';
import { analyticsKeys } from '../queryKeys';
import { DashboardRange } from '../types';

/** One call for the whole dashboard; the page prefetches it on the server. */
export const useDashboard = (blogSlug: string, range: DashboardRange) =>
  useQuery({
    queryKey: analyticsKeys.dashboard(blogSlug, range),
    queryFn: () => getDashboard(blogSlug, range),
    staleTime: 5 * 60 * 1000,
    placeholderData: (previous) => previous,
  });
