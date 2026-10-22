import DashboardContent from '@/components/dashboard/DashboardContent';
import { redirect } from 'next/navigation';
import { getMyProfile } from '@/features/member/api/profile';
import { dehydrate, HydrationBoundary } from '@tanstack/react-query';
import {
  getDashboardStatsAction,
  getPopularPostsAction,
  getTrafficDataAction,
} from '@/actions/dashboard';
import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getQueryClient } from '@/shared/lib/getQueryClient';

export default async function DashboardPage() {
  const [user, blogs] = await Promise.all([getMyProfile(), getMyBlogs()]);

  // The (main) layout already redirects blog-less users; this is direct-access defense.
  if (blogs.length === 0) {
    redirect('/dashboard/welcome');
  }

  const slug = blogs[0].slug;
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
      <DashboardContent user={user} blogs={blogs} />
    </HydrationBoundary>
  );
}
