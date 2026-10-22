'use client';

import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import { Camera, Heart, MessageCircleMore } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';

export interface PostSummary {
  id: string;
  slug: string;
  title: string;
  summary: string;
  metaTitle: string | null;
  metaDescription: string | null;
  featuredImageUrl: string | null;
  publishedAt: string;
  categoryName: string | null;
  likeCount: number;
  viewCount: number;
}

interface PostCardProps {
  blogSlug: string;
  post: PostSummary;
}

export default function PostCard({ blogSlug, post }: PostCardProps) {
  const postUrl = `/@${blogSlug}/${post.slug}`;

  return (
    <div className="flex flex-col space-y-2 border-b border-gray-200 py-2">
      {/* 상단: 작성자 정보 */}

      {/* 중앙: 컨텐츠 (제목, 요약, 썸네일) */}
      <Link href={postUrl} key={post.id} className="group block">
        <div className="flex items-start justify-between space-x-6">
          {/* 좌측: 제목 및 요약 */}
          <div className="flex min-w-0 flex-1 flex-col space-y-2">
            <h2 className="text-xl font-bold text-gray-900 group-hover:text-indigo-600">
              {post.title}
            </h2>
            <p className="line-clamp-2 text-gray-600">{post.summary}</p>
          </div>

          {/* 우측: 썸네일 이미지 */}
          <div className="relative h-24 w-24 flex-shrink-0 overflow-hidden rounded-lg sm:h-28 sm:w-36">
            {post.featuredImageUrl ? (
              <Image
                src={post.featuredImageUrl ?? '/images/default-thumbnail-01.png'}
                alt={`Thumbnail for ${post.title}`}
                fill
                style={{ objectFit: 'cover' }}
                sizes="(max-width: 640px) 112px, 160px"
                className="bg-gray-100"
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center bg-gray-100">
                <Camera className="h-10 w-10 text-gray-400" />
              </div>
            )}
          </div>
        </div>
      </Link>

      {/* 하단: 통계 정보 (좋아요, 댓글, 날짜) */}
      <div className="flex items-center space-x-4 text-sm text-gray-500">
        <div className="flex items-center">
          <Heart size={16} className="mr-1" />
          <span>{post.likeCount}</span>
        </div>
        <div className="flex items-center">
          <MessageCircleMore size={16} className="mr-1" />
          <span>0</span>
        </div>
        <span>.</span>
        <span>{formatPostDate(post.publishedAt)}</span>
      </div>
    </div>
  );
}
