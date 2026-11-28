'use client';

import { Button } from '@/shared/ui/button';
import { Heart } from 'lucide-react';
import Link from 'next/link';
import { useState, useTransition } from 'react';
import { likeAction, unlikeAction } from '../api/interactions';
import { interactionErrorMessage } from '../errors';
import { Viewer } from './SubscribeButton';

interface LikeButtonProps {
  postId: string;
  viewer: Viewer;
  initialLiked: boolean;
  initialCount: number;
  /** 로그인 후 돌아올 경로입니다. */
  loginRedirect: string;
}

/** 타인의 게시글에 좋아요를 토글하는 버튼을 렌더링합니다. (interaction 01-rules 흐름 2) */
export default function LikeButton({
  postId,
  viewer,
  initialLiked,
  initialCount,
  loginRedirect,
}: LikeButtonProps) {
  const [liked, setLiked] = useState(initialLiked);
  const [count, setCount] = useState(initialCount);
  const [isPending, startTransition] = useTransition();

  const content = (
    <>
      <Heart className={`h-4 w-4 ${liked ? 'fill-red-500 text-red-500' : ''}`} />
      <span className="tabular-nums">{count.toLocaleString()}</span>
    </>
  );

  if (viewer === 'owner') {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm text-gray-500">{content}</span>
    );
  }
  if (viewer === 'anonymous') {
    return (
      <Button asChild variant="outline" size="sm">
        <Link
          href={`/login?redirect=${encodeURIComponent(loginRedirect)}`}
          aria-label="좋아요 (로그인 필요)"
        >
          {content}
        </Link>
      </Button>
    );
  }

  const toggle = () =>
    startTransition(async () => {
      const result = liked ? await unlikeAction(postId) : await likeAction(postId);
      if (!result.success) {
        alert(interactionErrorMessage(result.error));
        return;
      }
      setCount((previous) => previous + (liked ? -1 : 1));
      setLiked(!liked);
    });

  return (
    <Button
      variant="outline"
      size="sm"
      disabled={isPending}
      onClick={toggle}
      aria-pressed={liked}
      aria-label="좋아요"
    >
      {content}
    </Button>
  );
}
