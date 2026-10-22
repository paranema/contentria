import { getCounts, isSubscribed } from '@/features/interaction/api/interactions';
import SubscribeButton from '@/features/interaction/components/SubscribeButton';
import Sidebar from '@/features/publishing/components/blog/Sidebar';
import { getBlogLayoutData } from '../../../_composition/blog';
import { viewerOf } from '../../../_composition/viewer';

interface SidebarSlotProps {
  params: Promise<{
    blogSlug: string;
  }>;
}

export default async function SidebarSlot({ params }: SidebarSlotProps) {
  const { blogSlug } = await params;
  const layout = await getBlogLayoutData(blogSlug);
  if (!layout) {
    return null;
  }

  // Interaction's subscribe button under the owner (04-context-map, 조회 조합: 블로그 레이아웃).
  const { blog } = layout;
  const [{ viewer }, counts] = await Promise.all([
    viewerOf(blog.ownerId),
    getCounts({ blogIds: [blog.blogId] }),
  ]);
  const subscribed = viewer === 'member' ? await isSubscribed(blog.slug) : false;

  return (
    <Sidebar
      blog={blog}
      owner={layout.owner}
      categories={layout.categories}
      actions={
        <SubscribeButton
          blogSlug={blog.slug}
          viewer={viewer}
          initialSubscribed={subscribed}
          initialCount={counts.subscribers[blog.blogId] ?? 0}
        />
      }
    />
  );
}
