import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import Link from 'next/link';
import { PostListItem } from '../../types';

interface PostCardProps {
  blogSlug: string;
  post: PostListItem;
}

/** A published post in a blog's list. Like and comment counts come with interaction (F5). */
export default function PostCard({ blogSlug, post }: PostCardProps) {
  return (
    <div className="flex flex-col space-y-2 border-b border-gray-200 py-2">
      <Link href={`/@${blogSlug}/${post.slug}`} className="group block">
        <div className="flex min-w-0 flex-1 flex-col space-y-2">
          <h2 className="text-xl font-bold text-gray-900 group-hover:text-indigo-600">
            {post.title}
          </h2>
          <p className="line-clamp-2 text-gray-600">{post.summary}</p>
        </div>
      </Link>

      {post.publishedAt && (
        <div className="flex items-center space-x-4 text-sm text-gray-500">
          <span>{formatPostDate(post.publishedAt)}</span>
        </div>
      )}
    </div>
  );
}
