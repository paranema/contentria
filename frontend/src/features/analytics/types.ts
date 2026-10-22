// The analytics context's API and the blog dashboard apps/api puts together
// (docs/contexts/analytics/02-implementation.md).

/** A reader opening a blog's home (no [postId]) or a post. [referrer] is document.referrer. */
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

/** One day of the trend; [date] is yyyy-MM-dd in Korea time. */
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

/** `GET /me/blogs/{slug}/dashboard`: the owner's numbers (analytics 01-rules 흐름 3). */
export interface DashboardView {
  summary: TrafficSummary;
  trend: DailyTraffic[];
  popularPosts: PopularPost[];
  publishedPosts: number;
}
