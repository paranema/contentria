import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import { Heart, MessageCircleMore } from 'lucide-react';
import Link from 'next/link';
import { PostListItem } from '../../types';

interface PostCardProps {
  blogSlug: string;
  post: PostListItem;
  /** 페이지에서 조합해 전달하는 좋아요 및 댓글 수입니다. */
  counts?: { likes: number; comments: number };
}

/** 블로그 글 목록에 표시되는 발행 글 카드입니다. */
export default function PostCard({ blogSlug, post, counts }: PostCardProps) {
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
          {counts && (
            <>
              <span className="flex items-center">
                <Heart size={16} className="mr-1" />
                {counts.likes}
              </span>
              <span className="flex items-center">
                <MessageCircleMore size={16} className="mr-1" />
                {counts.comments}
              </span>
            </>
          )}
          <span>{formatPostDate(post.publishedAt)}</span>
        </div>
      )}
    </div>
  );
}
