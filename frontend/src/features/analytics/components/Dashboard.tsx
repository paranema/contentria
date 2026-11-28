'use client';

import { useDashboard } from '../hooks/useDashboard';
import { DASHBOARD_RANGE } from '../queryKeys';
import PopularPostList from './PopularPostList';
import { Loader2 } from 'lucide-react';
import TrafficChart, { TRAFFIC_SERIES } from './TrafficChart';
import StatGroupCard from './StatGroupCard';

interface DashboardProps {
  blogSlug: string;
  /** 페이지에서 member 컨텍스트로부터 조합한 블로그 소유자 표시 이름입니다. */
  ownerName: string | null;
}

/** 단일 API 응답으로 조합된 블로그 관리 대시보드를 렌더링합니다. (analytics 01-rules 흐름 3) */
export default function Dashboard({ blogSlug, ownerName }: DashboardProps) {
  const { data, isFetching } = useDashboard(blogSlug, DASHBOARD_RANGE);
  const stats = data?.summary;

  return (
    <div className="space-y-6">
      {/* 글쓰기 버튼은 상단 공통 헤더에 일원화되어 있으므로 환영 문구만 표시합니다. */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">블로그관리 홈</h1>
        <p className="mt-1 text-sm text-gray-500">
          안녕하세요, {ownerName || '관리자'}님! 오늘의 블로그 현황입니다.
        </p>
      </div>

      {/* 조회수와 방문자 지표를 각각 오늘·어제·누적 3열 카드로 표시합니다. */}
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

      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-baseline gap-2">
            <h2 className="text-lg font-semibold">트래픽 현황</h2>
            <span className="text-sm text-gray-400">최근 30일</span>
          </div>
          {/* 차트 선과 동일한 색상 정의(TRAFFIC_SERIES)를 공유하여 범례를 표시합니다. */}
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
        {isFetching && !data ? (
          <div className="flex h-[280px] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-indigo-500" />
          </div>
        ) : (
          <TrafficChart data={data?.trend ?? []} />
        )}
      </div>

      {/* 글 관리 이동은 사이드바 메뉴가 담당하므로 별도 링크 없이 인기 게시글만 표시합니다. */}
      <div className="rounded-lg bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-baseline gap-2">
          <h2 className="text-lg font-semibold">인기 게시글</h2>
          <span className="text-sm text-gray-400">최근 30일 조회수 기준</span>
        </div>
        <PopularPostList posts={data?.popularPosts ?? []} blogSlug={blogSlug} />
      </div>
    </div>
  );
}
