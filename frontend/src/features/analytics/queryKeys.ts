import { DashboardRange } from './types';

// 서버 컴포넌트인 대시보드 페이지에서 프리페치할 때 값을 직접 읽을 수 있도록 'use client'를 선언하지 않습니다.

/** 대시보드 트래픽 추이의 기본 조회 기간(최근 30일)입니다. */
export const DASHBOARD_RANGE: DashboardRange = '30days';

export const analyticsKeys = {
  dashboard: (blogSlug: string, range: DashboardRange) =>
    ['analytics', 'dashboard', blogSlug, range] as const,
};
