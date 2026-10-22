import Sidebar from '@/features/publishing/components/blog/Sidebar';
import { getBlogLayoutData } from '../../../_composition/blog';

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

  return <Sidebar blog={layout.blog} owner={layout.owner} categories={layout.categories} />;
}
