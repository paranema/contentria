'use client';

import { Bell } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getUnreadCount } from '../api/notifications';

/** How often the bell asks; there is no push (01-rules 전제 2). */
const POLL_MS = 60_000;

/** The header's bell: the unread count, asked now and every minute, and the way to the inbox. */
export default function NotificationBell() {
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let alive = true;
    const ask = async () => {
      const count = await getUnreadCount();
      if (alive && count !== null) setUnread(count);
    };
    ask();
    const timer = setInterval(ask, POLL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  return (
    <Link
      href="/dashboard/notifications"
      className="relative rounded-full p-2 hover:bg-gray-100"
      aria-label={unread > 0 ? `알림 ${unread}개 안 읽음` : '알림'}
    >
      <Bell size={20} />
      {unread > 0 && (
        <span className="absolute right-0.5 top-0.5 min-w-4 rounded-full bg-red-500 px-1 text-center text-[10px] font-semibold leading-4 text-white">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  );
}
