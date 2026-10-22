'use client';

import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import { Button } from '@/shared/ui/button';
import Link from 'next/link';
import { useTransition } from 'react';
import { unsubscribeAction } from '../api/interactions';
import { interactionErrorMessage } from '../errors';
import { SubscribedBlog } from '../types';

/** The blogs the member subscribes to, each with a way to stop. */
export default function SubscriptionList({ subscriptions }: { subscriptions: SubscribedBlog[] }) {
  const [isPending, startTransition] = useTransition();

  if (subscriptions.length === 0) {
    return <p className="text-sm text-muted-foreground">아직 구독 중인 블로그가 없습니다.</p>;
  }

  const unsubscribe = (slug: string) =>
    startTransition(async () => {
      const result = await unsubscribeAction(slug);
      if (!result.success) alert(interactionErrorMessage(result.error));
    });

  return (
    <ul className="divide-y rounded-lg border bg-white">
      {subscriptions.map((subscription) => (
        <li key={subscription.blogId} className="flex items-center justify-between gap-4 px-4 py-3">
          <div className="min-w-0">
            <Link
              href={`/@${subscription.slug}`}
              className="font-medium text-gray-900 hover:text-indigo-600"
            >
              @{subscription.slug}
            </Link>
            <p className="text-xs text-gray-500">
              {formatPostDate(subscription.subscribedAt)}부터 구독
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            disabled={isPending}
            onClick={() => unsubscribe(subscription.slug)}
          >
            구독 취소
          </Button>
        </li>
      ))}
    </ul>
  );
}
