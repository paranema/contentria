import DashboardContent from '@/components/dashboard/DashboardContent';
import { redirect } from 'next/navigation';
import { getMyProfile } from '@/features/member/api/profile';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import {
  getDashboardStatsAction,
  getPopularPostsAction,
  getTrafficDataAction,
} from '@/actions/dashboard';
import { getMyBlogAction } from '@/actions/blog';
import { getQueryClient } from '@/shared/lib/getQueryClient';

export default async function DashboardPage() {
  const [user, blogInfos] = await Promise.all([getMyProfile(), getMyBlogAction()]);

  // The (main) layout already redirects blog-less users; this is direct-access defense.
  if (!blogInfos || blogInfos.length === 0) {
    redirect('/dashboard/welcome');
  }

  const slug = blogInfos[0].slug;
  const queryClient = getQueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: ['dashboard', 'stats', slug],
      queryFn: () => getDashboardStatsAction(slug),
    }),
    queryClient.prefetchQuery({
      queryKey: ['dashboard', 'popularPosts', slug],
      queryFn: () => getPopularPostsAction(slug),
    }),
    queryClient.prefetchQuery({
      queryKey: ['dashboard', 'traffic', slug, '2weeks'],
      queryFn: () => getTrafficDataAction(slug, '2weeks'),
    }),
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DashboardContent user={user} blogInfos={blogInfos} />
    </HydrationBoundary>
  );
}
