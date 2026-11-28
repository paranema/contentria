import type { Metadata } from 'next';
import BlogHeader from '@/app/user/[blogSlug]/_components/BlogHeader';
import Footer from '@/app/_components/Footer';
import { notFound } from 'next/navigation';
import { getBlogLayoutData } from '../../_composition/blog';

interface BlogLayoutProps {
  children: React.ReactNode;
  sidebar: React.ReactNode;
  params: Promise<{ blogSlug: string }>;
}

export async function generateMetadata({ params }: BlogLayoutProps): Promise<Metadata> {
  const { blogSlug } = await params;
  const layout = await getBlogLayoutData(blogSlug);

  return {
    title: layout?.blog.title || 'Blog',
    description: layout?.blog.description || 'Welcome to my blog',
    openGraph: {
      title: layout?.blog.title || 'Blog',
      description: layout?.blog.description || 'Welcome to my blog',
    },
  };
}

/**
 * 공개 블로그 공통 레이아웃입니다. 병렬 라우트(`@sidebar`) 슬롯을 `sidebar` prop으로 전달받아 함께 렌더링합니다.
 */
export default async function BlogLayout({ children, sidebar, params }: BlogLayoutProps) {
  const { blogSlug } = await params;
  if (!(await getBlogLayoutData(blogSlug))) {
    notFound();
  }

  return (
    <>
      <div className="flex min-h-[100dvh] flex-col bg-white">
        <BlogHeader blogSlug={blogSlug} />

        <div className="mx-auto flex w-full max-w-[1440px] flex-1 flex-col px-4 py-8 sm:px-6 lg:flex-row lg:gap-12 lg:py-12">
          {sidebar}

          <main className="min-w-0 flex-1">{children}</main>
        </div>
      </div>
      <Footer />
    </>
  );
}
