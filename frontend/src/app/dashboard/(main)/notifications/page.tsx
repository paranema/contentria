import NotificationList from '@/features/notification/components/NotificationList';
import { Metadata } from 'next';
import { loadNotifications } from '../../../_composition/notifications';

export const metadata: Metadata = {
  title: '알림 | 대시보드',
};

export default async function NotificationsPage() {
  const initial = await loadNotifications();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-800">알림</h1>
        <p className="mt-1 text-sm text-gray-500">
          구독한 블로그의 새 글, 내 글과 댓글에 달린 댓글을 알려드립니다.
        </p>
      </div>
      <NotificationList initial={initial} loadMore={loadNotifications} />
    </div>
  );
}
