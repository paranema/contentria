import { Metadata } from 'next';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkDirective from 'remark-directive';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import rehypeSlug from 'rehype-slug';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import rehypeShiftHeading from 'rehype-shift-heading';
import SyntaxHighlighter from 'react-syntax-highlighter/dist/esm/default-highlight';
import { github } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import React from 'react';
import { Heading } from '@/types/common';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import { visit } from 'unist-util-visit';
import TableOfContents from '@/components/blog/TableOfContents';
import { getPostDetailAction } from '@/actions/post';
import { notFound } from 'next/navigation';
import GithubSlugger from 'github-slugger';
import { toString } from 'mdast-util-to-string';
import AnalyticsTracker from '@/components/analytics/AnalyticsTracker';
import { Separator } from '@/shared/ui/separator';
import { remarkAdmonitions } from '@/lib/markdown/remarkAdmonitions';
import Admonition, { isAdmonitionType } from '@/components/blog/Admonition';
import VideoPlayer from '@/components/blog/VideoPlayer';

export const dynamic = 'force-dynamic';

const sanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    div: [
      ...(defaultSchema.attributes?.div || []),
      ['className', /^admonition(-(?:note|tip|info|caution|warning|danger))?$/],
    ],
  },
};

interface PostDetailPageProps {
  params: Promise<{
    blogSlug: string;
    postSlug: string;
  }>;
}

export async function generateMetadata({ params }: PostDetailPageProps): Promise<Metadata> {
  const { blogSlug, postSlug } = await params;
  const postData = await getPostDetailAction(blogSlug, postSlug);

  if (!postData) {
    return { title: '게시물을 찾을 수 없습니다.' };
  }

  return {
    title: postData.post.title,
    description: postData.post.metaDescription || '',
  };
}

async function getHeadingsFromMarkdown(markdown: string): Promise<Heading[]> {
  const headings: Heading[] = [];
  const processor = unified().use(remarkParse);
  const tree = processor.parse(markdown);

  const slugger = new GithubSlugger();

  visit(tree, 'heading', (node) => {
    if (node.depth >= 1 && node.depth <= 2) {
      const text = toString(node);
      const id = slugger.slug(text);

      headings.push({
        id,
        text,
        level: node.depth,
      });
    }
  });

  return headings;
}

export default async function PostDetailPage({ params }: PostDetailPageProps) {
  const { blogSlug, postSlug } = await params;
  const postDetailResponse = await getPostDetailAction(blogSlug, postSlug);

  if (!postDetailResponse) {
    console.log('Post not found, invoking notFound()');
    notFound();
  }

  const { post, author, blogId, video } = postDetailResponse;
  const { contentMarkdown } = post;

  const headings = await getHeadingsFromMarkdown(contentMarkdown);
  console.log('Extracted headings:', headings);

  return (
    <>
      <AnalyticsTracker blogId={blogId} postId={post.id} />

      <div className="flex w-full flex-col items-start xl:flex-row xl:gap-12">
        <div className="w-full min-w-0 max-w-4xl flex-1">
          <header className="mb-8 lg:mb-10">
            <h1 className="mb-6 break-keep text-2xl font-extrabold leading-tight text-gray-900 sm:text-3xl lg:text-4xl">
              {post.title}
            </h1>
            <div className="flex items-center space-x-3 text-sm text-gray-500">
              <span className="font-semibold text-gray-900">{author.nickname}</span>
              <span>•</span>
              <time dateTime={post.publishedAt}>
                {new Date(post.publishedAt).toLocaleDateString('ko-KR')}
              </time>
            </div>
          </header>

          <Separator className="mb-8 lg:mb-10" />

          {video && <VideoPlayer video={video} />}

          <article className="prose prose-indigo max-w-none lg:prose-lg prose-headings:scroll-mt-24">
            <Markdown
              remarkPlugins={[remarkGfm, remarkDirective, remarkAdmonitions]}
              remarkRehypeOptions={{ allowDangerousHtml: true }}
              rehypePlugins={[
                rehypeRaw,
                [rehypeSanitize, sanitizeSchema],
                rehypeSlug,
                [rehypeAutolinkHeadings, { behavior: 'wrap' }],
                [rehypeShiftHeading, { shift: 1 }],
              ]}
              components={{
                code(props) {
                  const { children, className, node: _node, ref: _ref, ...rest } = props;
                  const match = /language-(\w+)/.exec(className || '');
                  return match ? (
                    <SyntaxHighlighter
                      {...rest}
                      PreTag="div"
                      children={String(children).replace(/\n$/, '')}
                      language={match[1]}
                      style={github}
                    />
                  ) : (
                    <code {...rest} className={className}>
                      {children}
                    </code>
                  );
                },
                div(props) {
                  const { className, children, node: _node, ...rest } = props;
                  const match = className?.match(/admonition-(\w+)/);
                  if (match && isAdmonitionType(match[1])) {
                    return <Admonition type={match[1]}>{children}</Admonition>;
                  }
                  return (
                    <div className={className} {...rest}>
                      {children}
                    </div>
                  );
                },
              }}
            >
              {contentMarkdown}
            </Markdown>
          </article>
        </div>

        <aside className="hidden w-64 shrink-0 xl:sticky xl:top-24 xl:block xl:max-h-[calc(100vh-8rem)] xl:overflow-y-auto">
          <TableOfContents headings={headings} />
        </aside>
      </div>
    </>
  );
}
