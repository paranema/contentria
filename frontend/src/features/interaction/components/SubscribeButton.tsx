'use client';

import { Button } from '@/shared/ui/button';
import { BellPlus, BellRing } from 'lucide-react';
import Link from 'next/link';
import { useState, useTransition } from 'react';
import { subscribeAction, unsubscribeAction } from '../api/interactions';
import { interactionErrorMessage } from '../errors';

/** 현재 열람자 유형(비로그인 독자, 블로그 소유자, 일반 로그인 회원)입니다. */
export type Viewer = 'anonymous' | 'owner' | 'member';

interface SubscribeButtonProps {
  blogSlug: string;
  viewer: Viewer;
  initialSubscribed: boolean;
  initialCount: number;
}

/** 타인의 블로그를 구독하거나 구독 취소하는 버튼을 렌더링합니다. (interaction 01-rules 흐름 1) */
export default function SubscribeButton({
  blogSlug,
  viewer,
  initialSubscribed,
  initialCount,
}: SubscribeButtonProps) {
  const [subscribed, setSubscribed] = useState(initialSubscribed);
  const [count, setCount] = useState(initialCount);
  const [isPending, startTransition] = useTransition();

  const label = (
    <span className="text-sm text-muted-foreground">구독자 {count.toLocaleString()}명</span>
  );

  if (viewer === 'owner') return label;
  if (viewer === 'anonymous') {
    return (
      <div className="flex flex-col items-center gap-2">
        <Button asChild size="sm" className="w-full">
          <Link href={`/login?redirect=${encodeURIComponent(`/@${blogSlug}`)}`}>
            <BellPlus className="h-4 w-4" />
            구독하기
          </Link>
        </Button>
        {label}
      </div>
    );
  }

  const toggle = () =>
    startTransition(async () => {
      const result = subscribed
        ? await unsubscribeAction(blogSlug)
        : await subscribeAction(blogSlug);
      if (!result.success) {
        alert(interactionErrorMessage(result.error));
        return;
      }
      setCount((previous) => previous + (subscribed ? -1 : 1));
      setSubscribed(!subscribed);
    });

  return (
    <div className="flex flex-col items-center gap-2">
      <Button
        size="sm"
        variant={subscribed ? 'outline' : 'default'}
        className="w-full"
        disabled={isPending}
        onClick={toggle}
      >
        {subscribed ? <BellRing className="h-4 w-4" /> : <BellPlus className="h-4 w-4" />}
        {subscribed ? '구독 중' : '구독하기'}
      </Button>
      {label}
    </div>
  );
}
