'use client';

import { formatPostDate } from '@/shared/lib/dateFormatUtil';
import { Button } from '@/shared/ui/button';
import { useRouter } from 'next/navigation';
import { useState, useTransition } from 'react';
import { markAllReadAction, markReadAction } from '../api/notifications';
import { NotificationEntries, NotificationEntry } from '../types';

interface NotificationListProps {
  initial: NotificationEntries;
  /** The page's server action for the next page. */
  loadMore: (cursor: string) => Promise<NotificationEntries>;
}

function sentence(entry: NotificationEntry): string {
  // A post hidden or deleted since: nothing of it is named.
  if (!entry.post) return '지금은 볼 수 없는 글에 대한 알림입니다.';
  const who = entry.actorName ?? '알 수 없는 회원';
  const what = `'${entry.post.title}'`;
  switch (entry.kind) {
    case 'NEW_POST':
      return `구독한 블로그에 새 글 ${what}이(가) 올라왔습니다.`;
    case 'COMMENT_ON_POST':
      return `${who}님이 ${what}에 댓글을 남겼습니다.`;
    case 'REPLY_TO_COMMENT':
      return `${who}님이 ${what}의 내 댓글에 답글을 남겼습니다.`;
  }
}

/** The inbox (notification 01-rules 흐름 3): newest first; opening one marks it read. */
export default function NotificationList({ initial, loadMore }: NotificationListProps) {
  const router = useRouter();
  const [entries, setEntries] = useState(initial.entries);
  const [cursor, setCursor] = useState(initial.nextCursor);
  const [isPending, startTransition] = useTransition();

  const open = (entry: NotificationEntry) =>
    startTransition(async () => {
      if (!entry.read) {
        await markReadAction(entry.notificationId);
        setEntries((list) =>
          list.map((e) => (e.notificationId === entry.notificationId ? { ...e, read: true } : e))
        );
      }
      if (entry.post) router.push(entry.post.href);
    });

  const readAll = () =>
    startTransition(async () => {
      const result = await markAllReadAction();
      if (result.success) setEntries((list) => list.map((e) => ({ ...e, read: true })));
    });

  const more = () =>
    startTransition(async () => {
      if (!cursor) return;
      const next = await loadMore(cursor);
      setEntries((list) => [...list, ...next.entries]);
      setCursor(next.nextCursor);
    });

  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">아직 알림이 없습니다.</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button
          variant="outline"
          size="sm"
          disabled={isPending || entries.every((e) => e.read)}
          onClick={readAll}
        >
          모두 읽음
        </Button>
      </div>
      <ul className="divide-y rounded-lg border bg-white">
        {entries.map((entry) => (
          <li key={entry.notificationId}>
            <button
              className={`flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 ${entry.read ? 'text-gray-500' : 'text-gray-900'}`}
              disabled={isPending}
              onClick={() => open(entry)}
            >
              <span
                className={`mt-2 h-2 w-2 shrink-0 rounded-full ${entry.read ? 'bg-transparent' : 'bg-indigo-500'}`}
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm">{sentence(entry)}</span>
                <span className="text-xs text-gray-400">{formatPostDate(entry.createdAt)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {cursor && (
        <Button variant="ghost" className="w-full" disabled={isPending} onClick={more}>
          알림 더 보기
        </Button>
      )}
    </div>
  );
}
