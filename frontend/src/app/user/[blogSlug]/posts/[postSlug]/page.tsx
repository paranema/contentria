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
import TableOfContents from '@/features/publishing/components/blog/TableOfContents';
import { getPublishedPost } from '@/features/publishing/api/posts';
import { notFound, permanentRedirect } from 'next/navigation';
import VisitTracker from '@/features/analytics/components/VisitTracker';
import { Separator } from '@/shared/ui/separator';
import { remarkAdmonitions } from '@/features/publishing/markdown/remarkAdmonitions';
import { headingsOf } from '@/features/publishing/markdown/headings';
import Admonition, { isAdmonitionType } from '@/features/publishing/components/blog/Admonition';
import { getPublicProfiles } from '@/features/member/api/profile';
import { getCounts, getMyLikes } from '@/features/interaction/api/interactions';
import CommentSection from '@/features/interaction/components/CommentSection';
import LikeButton from '@/features/interaction/components/LikeButton';
import { getPostVideo } from '@/features/media/api/videos';
import VideoPlayer from '@/features/media/components/VideoPlayer';
import { getBlogLayoutData } from '../../../../_composition/blog';
import { loadComments } from '../../../../_composition/comments';
import { viewerOf } from '../../../../_composition/viewer';

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
  const { blogSlug, postSlug } = slugsOf(await params);
  const lookup = await getPublishedPost(blogSlug, postSlug);

  if (!lookup || !('post' in lookup)) {
    return { title: '게시물을 찾을 수 없습니다.' };
  }

  return {
    title: lookup.post.title,
    description: lookup.post.summary,
  };
}

/**
 * 한글 등 비 ASCII 문자가 포함된 동적 세그먼트는 퍼센트 인코딩된 상태로 전달됩니다.
 * API 호출 함수에서 자체적으로 인코딩하므로 중복 인코딩을 막기 위해 먼저 한 번 디코딩합니다.
 */
function slugsOf(params: { blogSlug: string; postSlug: string }) {
  return {
    blogSlug: decodeURIComponent(params.blogSlug),
    postSlug: decodeURIComponent(params.postSlug),
  };
}

export default async function PostDetailPage({ params }: PostDetailPageProps) {
  const { blogSlug, postSlug } = slugsOf(await params);
  const lookup = await getPublishedPost(blogSlug, postSlug);
  if (!lookup) {
    notFound();
  }
  // 이전 슬러그로 접근한 경우 현재 게시글 주소로 영구 리다이렉트합니다. (publishing 01-rules, 옛 slug)
  if ('moved' in lookup) {
    permanentRedirect(`/@${lookup.moved.blogSlug}/${encodeURIComponent(lookup.moved.currentSlug)}`);
  }

  // 게시글 본문(publishing), 작성자(member), 동영상(media), 좋아요·댓글(interaction)을 병렬로 조회해 조합합니다. (04-context-map, 조회 조합: 글 상세)
  const { post } = lookup;
  const [[author], layout, video, counts, comments, { profile, viewer }] = await Promise.all([
    getPublicProfiles([post.authorId]),
    getBlogLayoutData(blogSlug),
    getPostVideo(post.postId),
    getCounts({ postIds: [post.postId] }),
    loadComments(post.postId),
    viewerOf(post.authorId),
  ]);
  const liked = viewer === 'member' && (await getMyLikes([post.postId])).has(post.postId);
  const here = `/@${post.blogSlug}/${post.slug}`;
  const categoryName = layout?.categories.find((category) => category.id === post.categoryId)?.name;
  const headings = headingsOf(post.body);

  return (
    <>
      <VisitTracker blogId={post.blogId} postId={post.postId} />

      <div className="flex w-full flex-col items-start xl:flex-row xl:gap-12">
        <div className="w-full min-w-0 max-w-4xl flex-1">
          <header className="mb-8 lg:mb-10">
            <h1 className="mb-6 break-keep text-2xl font-extrabold leading-tight text-gray-900 sm:text-3xl lg:text-4xl">
              {post.title}
            </h1>
            <div className="flex items-center space-x-3 text-sm text-gray-500">
              <span className="font-semibold text-gray-900">
                {author?.displayName ?? `@${post.blogSlug}`}
              </span>
              {categoryName && (
                <>
                  <span>•</span>
                  <span>{categoryName}</span>
                </>
              )}
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
              {post.body}
            </Markdown>
          </article>

          <div className="mt-10 flex justify-center">
            <LikeButton
              postId={post.postId}
              viewer={viewer}
              initialLiked={liked}
              initialCount={counts.likes[post.postId] ?? 0}
              loginRedirect={here}
            />
          </div>

          <CommentSection
            postId={post.postId}
            initial={comments}
            viewer={
              profile
                ? {
                    accountId: profile.accountId,
                    author: { displayName: profile.displayName, pictureUrl: profile.pictureUrl },
                  }
                : null
            }
            loadMore={loadComments}
            loginRedirect={here}
          />
        </div>

        <aside className="hidden w-64 shrink-0 xl:sticky xl:top-24 xl:block xl:max-h-[calc(100vh-8rem)] xl:overflow-y-auto">
          <TableOfContents headings={headings} />
        </aside>
      </div>
    </>
  );
}
