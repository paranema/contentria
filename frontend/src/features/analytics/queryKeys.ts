import { DashboardRange } from './types';

// Not in a 'use client' module: the dashboard page, a server component, prefetches with these,
// and a server component gets only a reference to anything a client module exports.

/** The trend the dashboard shows: the last 30 days, with no range picker for now. */
export const DASHBOARD_RANGE: DashboardRange = '30days';

export const analyticsKeys = {
  dashboard: (blogSlug: string, range: DashboardRange) =>
    ['analytics', 'dashboard', blogSlug, range] as const,
};
