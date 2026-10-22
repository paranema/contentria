'use client';

import { Button } from '@/shared/ui/button';
import { BellPlus, BellRing } from 'lucide-react';
import Link from 'next/link';
import { useState, useTransition } from 'react';
import { subscribeAction, unsubscribeAction } from '../api/interactions';
import { interactionErrorMessage } from '../errors';

/** Who is looking: someone not signed in, the blog's owner, or another member. */
export type Viewer = 'anonymous' | 'owner' | 'member';

interface SubscribeButtonProps {
  blogSlug: string;
  viewer: Viewer;
  initialSubscribed: boolean;
  initialCount: number;
}

/** Interaction 01-rules 흐름 1: a member subscribes to someone else's blog, and sees new posts in the inbox. */
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
