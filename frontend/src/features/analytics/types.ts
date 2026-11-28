// analytics 컨텍스트 API 및 apps/api가 조합하는 대시보드 응답 타입을 정의합니다. (docs/contexts/analytics/02-implementation.md)

/** 블로그 홈(postId 없음) 또는 게시글 방문 기록 요청입니다. referrer에는 document.referrer를 전달합니다. */
export interface VisitRequest {
  blogId: string;
  postId?: string | null;
  referrer?: string | null;
}

export type DashboardRange = '2weeks' | '30days' | '90days';

export interface TrafficSummary {
  todayVisitors: number;
  todayViews: number;
  yesterdayVisitors: number;
  yesterdayViews: number;
  totalVisitors: number;
  totalViews: number;
}

/** 일별 트래픽 추이 항목이며, date는 한국 시간 기준 yyyy-MM-dd 형식입니다. */
export interface DailyTraffic {
  date: string;
  visitors: number;
  views: number;
}

export interface PopularPost {
  postId: string;
  title: string;
  slug: string;
  views: number;
}

/** 블로그 소유자용 대시보드 통합 조회 응답입니다. (analytics 01-rules 흐름 3) */
export interface DashboardView {
  summary: TrafficSummary;
  trend: DailyTraffic[];
  popularPosts: PopularPost[];
  publishedPosts: number;
}
