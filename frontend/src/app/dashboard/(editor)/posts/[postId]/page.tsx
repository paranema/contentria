import { getMyBlogs } from '@/features/publishing/api/blogs';
import { getCategoryTree } from '@/features/publishing/api/categories';
import { getMyPost } from '@/features/publishing/api/posts';
import PostEditor from '../_components/PostEditor';
import { Metadata } from 'next';

interface EditPostPageProps {
  params: Promise<{
    postId: string;
  }>;
}

export async function generateMetadata(): Promise<Metadata> {
  return {
    title: '게시글 수정',
    description: '작성한 게시글을 수정합니다.',
  };
}

export default async function EditPostPage({ params }: EditPostPageProps) {
  const { postId } = await params;
  const [blogs, post] = await Promise.all([getMyBlogs(), getMyPost(postId)]);

  const blog = blogs[0];
  if (!blog) {
    return <div>블로그를 먼저 생성해주세요.</div>;
  }
  if (!post) {
    return <div>게시글을 찾을 수 없습니다.</div>;
  }

  const tree = await getCategoryTree(blog.slug);
  return <PostEditor blogSlug={blog.slug} categories={tree?.categories ?? []} initialData={post} />;
}
