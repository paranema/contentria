import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getCategoryTree } from '@/features/publishing/api/categories';
import PostEditor from '../_components/PostEditor';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: '새 글 작성',
  description: '대시보드에서 새 블로그 글을 작성합니다.',
};

export default async function NewPostPage() {
  const blog = (await getMyBlogs())[0];
  if (!blog) {
    return <div>블로그를 먼저 생성해주세요.</div>;
  }

  const tree = await getCategoryTree(blog.slug);
  return <PostEditor blogSlug={blog.slug} categories={tree?.categories ?? []} />;
}
