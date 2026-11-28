import { PopularPost } from '../types';
import Link from 'next/link';
import React from 'react';

interface PopularPostListProps {
  posts: PopularPost[];
  /** 공개 게시글 주소(/@{blogSlug}/{postSlug})를 구성하기 위한 블로그 슬러그입니다. */
  blogSlug: string;
}

/** 인기 게시글 순위 목록을 표시하며, 클릭 시 발행된 글을 새 탭으로 엽니다. */
export default function PopularPostList({ posts, blogSlug }: PopularPostListProps) {
  if (!posts || posts.length === 0) {
    return (
      <div className="flex h-[200px] items-center justify-center rounded-lg bg-gray-50">
        <p className="text-sm text-gray-500">아직 인기 게시글이 없습니다.</p>
      </div>
    );
  }

  return (
    <ol className="space-y-0.5">
      {posts.map((post, index) => (
        <li key={post.postId}>
          <Link
            href={`/@${blogSlug}/${post.slug}`}
            target="_blank"
            className="flex items-center gap-3 rounded-md px-2 py-1.5 transition-colors hover:bg-gray-50"
          >
            <span className="w-5 shrink-0 text-right text-sm font-semibold tabular-nums text-primary">
              {index + 1}.
            </span>
            <span className="truncate text-sm text-gray-800">{post.title}</span>
          </Link>
        </li>
      ))}
    </ol>
  );
}
