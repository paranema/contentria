'use client';

import { Bell } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getUnreadCount } from '../api/notifications';

/** 별도 푸시 채널 없이 읽지 않은 알림 수를 주기적으로 조회하는 폴링 간격(1분)입니다. (01-rules 전제 2) */
const POLL_MS = 60_000;

/** 상단 헤더에 읽지 않은 알림 배지와 알림함 이동 링크를 렌더링합니다. */
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
