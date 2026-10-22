import { getDashboard } from '@/features/analytics/api/dashboard';
import Dashboard from '@/features/analytics/components/Dashboard';
import { analyticsKeys, DASHBOARD_RANGE } from '@/features/analytics/queryKeys';
import { getMyProfile } from '@/features/member/api/profile';
import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getQueryClient } from '@/shared/lib/getQueryClient';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import { redirect } from 'next/navigation';

export default async function DashboardPage() {
  const [profile, blogs] = await Promise.all([getMyProfile(), getMyBlogs()]);

  // The (main) layout already redirects blog-less users; this is direct-access defense.
  if (blogs.length === 0) {
    redirect('/dashboard/welcome');
  }

  const slug = blogs[0].slug;
  const queryClient = getQueryClient();
  await queryClient.prefetchQuery({
    queryKey: analyticsKeys.dashboard(slug, DASHBOARD_RANGE),
    queryFn: () => getDashboard(slug, DASHBOARD_RANGE),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <Dashboard blogSlug={slug} ownerName={profile.displayName} />
    </HydrationBoundary>
  );
}
