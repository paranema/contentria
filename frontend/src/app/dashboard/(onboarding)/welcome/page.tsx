import { redirect } from 'next/navigation';
import CreateBlogWelcome from '@/features/publishing/components/dashboard/CreateBlogWelcome';
import { getMyBlogs } from '@/features/publishing/api/blogs';

export default async function WelcomePage() {
  const blogs = await getMyBlogs();

  // 이미 블로그를 보유한 회원이 온보딩 화면에 접근하면 대시보드로 이동시킵니다.
  if (blogs.length > 0) {
    redirect('/dashboard');
  }

  return <CreateBlogWelcome />;
}
