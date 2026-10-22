import { redirect } from 'next/navigation';
import CreateBlogWelcome from '@/features/publishing/components/dashboard/CreateBlogWelcome';
import { getMyBlogs } from '@/features/publishing/api/blogs';

export default async function WelcomePage() {
  const blogs = await getMyBlogs();

  // Reverse guard: users who already own a blog don't belong on the onboarding screen.
  if (blogs.length > 0) {
    redirect('/dashboard');
  }

  return <CreateBlogWelcome />;
}
