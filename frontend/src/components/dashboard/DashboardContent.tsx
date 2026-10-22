'use client';

import {
  useDashboadStatsQuery,
  usePopularPostsQuery,
  useTrafficChartQuery,
} from '@/hooks/queries/useDashboardQueries';
import PopularPostList from './PopularPostList';
import { Loader2 } from 'lucide-react';
import TrafficChart, { TRAFFIC_SERIES } from './TrafficChart';
import StatGroupCard from './StatGroupCard';
import { MyProfile } from '@/features/member/types';
import { BlogView } from '@/features/publishing/types';

interface DashboardContentProps {
  user: MyProfile | null;
  blogs: BlogView[];
}

export default function DashboardContent({ user, blogs }: DashboardContentProps) {
  const slug = blogs[0]?.slug;

  const { data: stats } = useDashboadStatsQuery(slug!);
  const { data: popularPosts } = usePopularPostsQuery(slug!);
  // 기간 선택 없이 최근 30일 고정
  const { data: trafficChart, isFetching: isTrafficFetching } = useTrafficChartQuery(
    slug!,
    '30days'
  );

  return (
    <div className="space-y-6">
      {/* 환영 헤더 — 글쓰기 CTA는 페이지가 아니라 상단 헤더에 일원화돼 있다 */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">블로그관리 홈</h1>
        <p className="mt-1 text-sm text-gray-500">
          안녕하세요, {user?.displayName || '관리자'}님! 오늘의 블로그 현황입니다.
        </p>
      </div>

      {/* 통계: 조회수/방문자 그룹 카드 — 각 카드에 오늘·어제·누적 3열 (티스토리식) */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <StatGroupCard
          title="조회수"
          items={[
            { label: '오늘', value: stats?.todayViews ?? 0 },
            { label: '어제', value: stats?.yesterdayViews ?? 0 },
            { label: '누적', value: stats?.totalViews ?? 0 },
          ]}
        />
        <StatGroupCard
          title="방문자"
          items={[
            { label: '오늘', value: stats?.todayVisitors ?? 0 },
            { label: '어제', value: stats?.yesterdayVisitors ?? 0 },
            { label: '누적', value: stats?.totalVisitors ?? 0 },
          ]}
        />
      </div>

      {/* 트래픽 차트 — 전체 폭 */}
      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-baseline gap-2">
            <h2 className="text-lg font-semibold">트래픽 현황</h2>
            <span className="text-sm text-gray-400">최근 30일</span>
          </div>
          {/* 범례: 차트의 TRAFFIC_SERIES와 같은 색 정의를 공유 */}
          <div className="flex items-center gap-4">
            {TRAFFIC_SERIES.map((series) => (
              <span key={series.key} className="flex items-center gap-1.5 text-sm text-gray-600">
                <span
                  className="h-0.5 w-4 rounded-full"
                  style={{ backgroundColor: series.color }}
                />
                {series.name}
              </span>
            ))}
          </div>
        </div>
        {isTrafficFetching ? (
          <div className="flex h-[280px] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-indigo-500" />
          </div>
        ) : (
          <TrafficChart data={trafficChart || []} />
        )}
      </div>

      {/* 인기 글 — 다음 행. 글 관리로 가는 링크는 사이드바 메뉴가 담당하므로 두지 않는다 */}
      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-baseline gap-2">
          <h2 className="text-lg font-semibold">인기 게시글</h2>
          <span className="text-sm text-gray-400">최근 30일 조회수 기준</span>
        </div>
        <PopularPostList posts={popularPosts || []} blogSlug={slug!} />
      </div>
    </div>
  );
}
